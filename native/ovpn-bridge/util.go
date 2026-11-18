package main

import (
	"bufio"
	"io"
	"net"
)

func ioReadFull(r *bufio.Reader, b []byte) (int, error) { return io.ReadFull(r, b) }

func copyBuffered(dst net.Conn, src io.Reader) (int64, error) {
	n, err := io.Copy(dst, src)
	if tc, ok := dst.(interface{ CloseWrite() error }); ok {
		_ = tc.CloseWrite()
	}
	return n, err
}
