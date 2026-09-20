// Command chapters は章を選んで実行する。使い方: go run ./cmd/chapters chapter01
package main

import (
	"fmt"
	"io"
	"os"
	"sort"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter01"
)

var chapters = map[string]func(io.Writer) error{
	"chapter01": chapter01.Run,
}

func main() {
	if len(os.Args) == 2 {
		if run, ok := chapters[os.Args[1]]; ok {
			if err := run(os.Stdout); err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}

			return
		}
	}

	names := make([]string, 0, len(chapters))
	for name := range chapters {
		names = append(names, name)
	}

	sort.Strings(names)
	fmt.Fprintf(os.Stderr, "使い方: go run ./cmd/chapters (%s)\n", joinNames(names))
	os.Exit(1)
}

func joinNames(names []string) string {
	joined := ""
	for i, name := range names {
		if i > 0 {
			joined += " | "
		}

		joined += name
	}

	return joined
}
