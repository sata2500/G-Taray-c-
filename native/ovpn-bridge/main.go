// gt-ovpn-bridge: OpenVPN'i telefonun VPN iznine gerek kalmadan yalnızca G-Tarayıcı için çalıştırır.
//
// OpenVPN, Android modunda (TARGET_ANDROID) derlenir ve tünel cihazını kendisi açmak yerine yönetim
// kanalından ister ("OPENTUN"). Bu köprü ona gerçek bir tun yerine bir soket çifti verir; OpenVPN'in
// ürettiği IP paketleri kullanıcı alanında çalışan bir TCP/IP yığınına (gVisor netstack) aktarılır.
// Tarayıcı, bu yığın üzerinden bağlanan yerel bir SOCKS5 vekil sunucusunu kullanır.
//
// Lisans: GPL-3.0-or-later
package main

import (
	"bufio"
	"context"
	"flag"
	"fmt"
	"net"
	"net/netip"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"

	"golang.org/x/sys/unix"
	"golang.zx2c4.com/wireguard/tun"
	"golang.zx2c4.com/wireguard/tun/netstack"
)

var (
	openvpnPath = flag.String("openvpn", "", "openvpn çalıştırılabilir dosyası")
	configPath  = flag.String("config", "", ".ovpn yapılandırma dosyası")
	workDir     = flag.String("workdir", ".", "çalışma klasörü")
	socksAddr   = flag.String("socks", "127.0.0.1:1080", "SOCKS5 dinleme adresi")
	authFile    = flag.String("auth", "", "kullanıcı adı ve parola (iki satır)")
	keyPassFile = flag.String("keypass", "", "özel anahtar parolası (tek satır)")
)

func logf(format string, args ...any) { fmt.Printf("GTOVPN "+format+"\n", args...) }

// tunnel: o anki sanal ağ (OpenVPN her yeniden bağlanmada OPENTUN ile yenisini ister).
type tunnel struct {
	mu   sync.RWMutex
	net  *netstack.Net
	dev  tun.Device
	sock int
}

func (t *tunnel) current() *netstack.Net {
	t.mu.RLock()
	defer t.mu.RUnlock()
	return t.net
}

type ifconfig struct {
	local  netip.Addr
	local6 netip.Addr
	mtu    int
	dns    []netip.Addr
}

func main() {
	flag.Parse()
	if *openvpnPath == "" || *configPath == "" {
		fmt.Fprintln(os.Stderr, "kullanım: gt-ovpn-bridge -openvpn PATH -config FILE -workdir DIR -socks ADDR")
		os.Exit(2)
	}
	// OpenVPN --cd ile klasör değiştirir; tüm yollar mutlak olmalıdır.
	for _, p := range []*string{workDir, configPath, authFile, keyPassFile} {
		if *p != "" {
			if abs, err := filepath.Abs(*p); err == nil {
				*p = abs
			}
		}
	}
	sockPath := filepath.Join(*workDir, "mgmt.sock")
	_ = os.Remove(sockPath)
	listener, err := net.ListenUnix("unix", &net.UnixAddr{Name: sockPath, Net: "unix"})
	if err != nil {
		logf("FATAL yönetim soketi: %v", err)
		os.Exit(1)
	}
	defer os.Remove(sockPath)

	cmd := exec.Command(*openvpnPath,
		"--config", *configPath,
		"--cd", *workDir,
		"--management", sockPath, "unix",
		"--management-client",
		"--management-query-passwords",
		"--management-hold",
		"--verb", "3",
	)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stdout
	if err := cmd.Start(); err != nil {
		logf("FATAL openvpn başlatılamadı: %v", err)
		os.Exit(1)
	}

	t := &tunnel{sock: -1}
	go serveSocks(t)

	// Uygulama süreci sonlandırırsa OpenVPN'i de kapat.
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGTERM, syscall.SIGINT)
	go func() {
		<-signals
		_ = cmd.Process.Signal(syscall.SIGTERM)
		time.Sleep(2 * time.Second)
		_ = cmd.Process.Kill()
		os.Exit(0)
	}()

	go func() {
		_ = listener.SetDeadline(time.Now().Add(30 * time.Second))
		conn, err := listener.AcceptUnix()
		if err != nil {
			logf("FATAL OpenVPN yönetim kanalına bağlanmadı: %v", err)
			_ = cmd.Process.Kill()
			return
		}
		manage(conn, t)
	}()

	err = cmd.Wait()
	logf("EXIT openvpn sonlandı: %v", err)
	os.Exit(1)
}

// manage: OpenVPN yönetim protokolünü yürütür.
func manage(conn *net.UnixConn, t *tunnel) {
	raw, err := conn.SyscallConn()
	if err != nil {
		logf("FATAL %v", err)
		return
	}
	var cfg ifconfig
	cfg.mtu = 1500

	send := func(line string, fd int) {
		var oob []byte
		if fd >= 0 {
			oob = unix.UnixRights(fd)
		}
		_ = raw.Write(func(s uintptr) bool {
			_ = unix.Sendmsg(int(s), []byte(line+"\n"), oob, nil, 0)
			return true
		})
	}

	send("hold release", -1)
	send("state on", -1)

	reader := lineReader(conn, raw)
	for line := range reader {
		switch {
		case strings.HasPrefix(line, ">HOLD:"):
			send("hold release", -1)
		case strings.HasPrefix(line, ">PASSWORD:Need 'Auth'"):
			user, pass := readTwoLines(*authFile)
			send(fmt.Sprintf("username \"Auth\" %s", quote(user)), -1)
			send(fmt.Sprintf("password \"Auth\" %s", quote(pass)), -1)
		case strings.HasPrefix(line, ">PASSWORD:Need 'Private Key'"):
			pass, _ := readTwoLines(*keyPassFile)
			send(fmt.Sprintf("password \"Private Key\" %s", quote(pass)), -1)
		case strings.HasPrefix(line, ">PASSWORD:Verification Failed"):
			logf("AUTH_FAILED kullanıcı adı veya parola hatalı")
		case strings.HasPrefix(line, ">NEED-OK:"):
			typ, payload := parseNeed(line)
			switch typ {
			case "IFCONFIG":
				// "10.8.0.2 255.255.255.0 1500 subnet"
				f := strings.Fields(payload)
				if len(f) >= 3 {
					cfg.local, _ = netip.ParseAddr(f[0])
					fmt.Sscanf(f[2], "%d", &cfg.mtu)
				}
				send("needok 'IFCONFIG' ok", -1)
			case "IFCONFIG6":
				f := strings.Fields(payload)
				if len(f) >= 1 {
					if p, err := netip.ParsePrefix(f[0]); err == nil {
						cfg.local6 = p.Addr()
					}
				}
				send("needok 'IFCONFIG6' ok", -1)
			case "DNSSERVER", "DNS6SERVER":
				if a, err := netip.ParseAddr(strings.TrimSpace(payload)); err == nil {
					cfg.dns = append(cfg.dns, a)
				}
				send(fmt.Sprintf("needok '%s' ok", typ), -1)
			case "PERSIST_TUN_ACTION":
				send("needok 'PERSIST_TUN_ACTION' OPEN_BEFORE_CLOSE", -1)
			case "OPENTUN":
				fd, err := openTunnel(t, cfg)
				if err != nil {
					logf("FATAL sanal ağ oluşturulamadı: %v", err)
					send("needok 'OPENTUN' cancel", -1)
					continue
				}
				send("needok 'OPENTUN' ok", fd)
				_ = unix.Close(fd) // OpenVPN kendi kopyasını aldı.
				cfg.dns = nil
			default:
				// ROUTE, ROUTE6, PROTECTFD, DNSDOMAIN, HTTPPROXY: onayla.
				send(fmt.Sprintf("needok '%s' ok", typ), -1)
			}
		case strings.HasPrefix(line, ">STATE:"):
			f := strings.Split(strings.TrimPrefix(line, ">STATE:"), ",")
			if len(f) >= 3 && f[1] == "EXITING" && strings.Contains(f[2], "auth-failure") {
				logf("AUTH_FAILED kullanıcı adı veya parola hatalı")
			}
			if len(f) >= 2 {
				logf("STATE %s", f[1])
				if f[1] == "CONNECTED" {
					logf("READY %s", *socksAddr)
				}
			}
		case strings.Contains(line, "AUTH_FAILED"):
			logf("AUTH_FAILED kullanıcı adı veya parola hatalı")
		case strings.HasPrefix(line, ">FATAL:"):
			logf("FATAL %s", strings.TrimPrefix(line, ">FATAL:"))
		}
	}
	logf("EXIT yönetim kanalı kapandı")
}

// lineReader: yönetim kanalından satır okur; OpenVPN'in gönderdiği dosya tanımlayıcılarını
// (ör. PROTECTFD) alıp kapatır.
func lineReader(conn *net.UnixConn, raw syscall.RawConn) <-chan string {
	out := make(chan string, 16)
	go func() {
		defer close(out)
		buf := make([]byte, 4096)
		oob := make([]byte, unix.CmsgSpace(4*4))
		var pending strings.Builder
		for {
			n, oobn, _, _, err := conn.ReadMsgUnix(buf, oob)
			if oobn > 0 {
				if msgs, perr := unix.ParseSocketControlMessage(oob[:oobn]); perr == nil {
					for _, m := range msgs {
						if fds, ferr := unix.ParseUnixRights(&m); ferr == nil {
							for _, fd := range fds {
								_ = unix.Close(fd)
							}
						}
					}
				}
			}
			if n > 0 {
				pending.Write(buf[:n])
				text := pending.String()
				for {
					i := strings.IndexByte(text, '\n')
					if i < 0 {
						break
					}
					out <- strings.TrimRight(text[:i], "\r")
					text = text[i+1:]
				}
				pending.Reset()
				pending.WriteString(text)
			}
			if err != nil {
				return
			}
		}
	}()
	_ = raw
	return out
}

// parseNeed: ">NEED-OK:Need 'IFCONFIG' confirmation MSG:10.8.0.2 ..." → ("IFCONFIG", "10.8.0.2 ...")
func parseNeed(line string) (string, string) {
	typ := ""
	if a := strings.IndexByte(line, '\''); a >= 0 {
		if b := strings.IndexByte(line[a+1:], '\''); b >= 0 {
			typ = line[a+1 : a+1+b]
		}
	}
	payload := ""
	if i := strings.Index(line, "MSG:"); i >= 0 {
		payload = line[i+4:]
	}
	return typ, payload
}

// openTunnel: OpenVPN'e verilecek soket çiftini ve ona bağlı kullanıcı alanı ağ yığınını kurar.
func openTunnel(t *tunnel, cfg ifconfig) (int, error) {
	if !cfg.local.IsValid() {
		return -1, fmt.Errorf("sunucu IP adresi atamadı")
	}
	fds, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_DGRAM, 0)
	if err != nil {
		return -1, err
	}
	addrs := []netip.Addr{cfg.local}
	if cfg.local6.IsValid() {
		addrs = append(addrs, cfg.local6)
	}
	dns := cfg.dns
	if len(dns) == 0 {
		dns = []netip.Addr{netip.MustParseAddr("1.1.1.1"), netip.MustParseAddr("9.9.9.9")}
	}
	mtu := cfg.mtu
	if mtu <= 0 || mtu > 9000 {
		mtu = 1500
	}
	dev, tnet, err := netstack.CreateNetTUN(addrs, dns, mtu)
	if err != nil {
		unix.Close(fds[0])
		unix.Close(fds[1])
		return -1, err
	}

	t.mu.Lock()
	if t.dev != nil {
		_ = t.dev.Close()
		_ = unix.Close(t.sock)
	}
	t.net, t.dev, t.sock = tnet, dev, fds[0]
	t.mu.Unlock()

	sock := fds[0]
	// OpenVPN → ağ yığını
	go func() {
		buf := make([]byte, 65536)
		for {
			n, err := unix.Read(sock, buf)
			if err != nil || n <= 0 {
				return
			}
			pkt := make([]byte, n)
			copy(pkt, buf[:n])
			if _, err := dev.Write([][]byte{pkt}, 0); err != nil {
				return
			}
		}
	}()
	// Ağ yığını → OpenVPN
	go func() {
		bufs := [][]byte{make([]byte, 65536)}
		sizes := []int{0}
		for {
			n, err := dev.Read(bufs, sizes, 0)
			if err != nil {
				return
			}
			for i := 0; i < n; i++ {
				if _, err := unix.Write(sock, bufs[i][:sizes[i]]); err != nil {
					return
				}
			}
		}
	}()
	logf("TUN %s mtu=%d dns=%v", cfg.local, mtu, dns)
	return fds[1], nil
}

func readTwoLines(path string) (string, string) {
	if path == "" {
		return "", ""
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return "", ""
	}
	lines := strings.SplitN(strings.ReplaceAll(string(data), "\r", ""), "\n", 3)
	first, second := "", ""
	if len(lines) > 0 {
		first = lines[0]
	}
	if len(lines) > 1 {
		second = lines[1]
	}
	return first, second
}

// quote: OpenVPN yönetim komutları için tırnaklı ve kaçışlı metin.
func quote(s string) string {
	s = strings.ReplaceAll(s, `\`, `\\`)
	s = strings.ReplaceAll(s, `"`, `\"`)
	return `"` + s + `"`
}

// --- SOCKS5 (yalnızca CONNECT, kimlik doğrulamasız; yalnızca 127.0.0.1'i dinler) -----------------

func serveSocks(t *tunnel) {
	ln, err := net.Listen("tcp", *socksAddr)
	if err != nil {
		logf("FATAL SOCKS dinlenemedi: %v", err)
		os.Exit(1)
	}
	for {
		c, err := ln.Accept()
		if err != nil {
			continue
		}
		go handleSocks(c, t)
	}
}

func handleSocks(c net.Conn, t *tunnel) {
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(30 * time.Second))
	r := bufio.NewReader(c)
	head := make([]byte, 2)
	if _, err := ioReadFull(r, head); err != nil || head[0] != 5 {
		return
	}
	methods := make([]byte, head[1])
	if _, err := ioReadFull(r, methods); err != nil {
		return
	}
	_, _ = c.Write([]byte{5, 0})

	req := make([]byte, 4)
	if _, err := ioReadFull(r, req); err != nil || req[1] != 1 {
		_, _ = c.Write([]byte{5, 7, 0, 1, 0, 0, 0, 0, 0, 0})
		return
	}
	var host string
	switch req[3] {
	case 1:
		b := make([]byte, 4)
		if _, err := ioReadFull(r, b); err != nil {
			return
		}
		host = net.IP(b).String()
	case 3:
		l, err := r.ReadByte()
		if err != nil {
			return
		}
		b := make([]byte, l)
		if _, err := ioReadFull(r, b); err != nil {
			return
		}
		host = string(b)
	case 4:
		b := make([]byte, 16)
		if _, err := ioReadFull(r, b); err != nil {
			return
		}
		host = net.IP(b).String()
	default:
		return
	}
	pb := make([]byte, 2)
	if _, err := ioReadFull(r, pb); err != nil {
		return
	}
	port := int(pb[0])<<8 | int(pb[1])

	tnet := t.current()
	if tnet == nil {
		_, _ = c.Write([]byte{5, 1, 0, 1, 0, 0, 0, 0, 0, 0})
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	remote, err := tnet.DialContext(ctx, "tcp", net.JoinHostPort(host, fmt.Sprint(port)))
	if err != nil {
		_, _ = c.Write([]byte{5, 5, 0, 1, 0, 0, 0, 0, 0, 0})
		return
	}
	defer remote.Close()
	_, _ = c.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0})
	_ = c.SetDeadline(time.Time{})

	done := make(chan struct{}, 2)
	go func() { _, _ = copyBuffered(remote, r); done <- struct{}{} }()
	go func() { _, _ = copyBuffered(c, remote); done <- struct{}{} }()
	<-done
}
