package main

import (
	"fmt"
	"os"

	clt "wiligsi.com/plump/v2/client"
	srv "wiligsi.com/plump/v2/server"
)

func main() {
	// Have different flag sets for client and server
	//clientCmd := flag.NewFlagSet("client", flag.ExitOnError)
	//serverCmd := flag.NewFlagSet("server", flag.ExitOnError)

	if len(os.Args) < 2 {
		fmt.Println("expected the client or server subcommand")
		os.Exit(1)
	}

	switch os.Args[1] {

	case "client":
		fmt.Println("Calling client code...")
		clt.StartClient()
	case "server":
		fmt.Println("Calling server code...")
		srv.StartServer()
	default:
		fmt.Printf("Subcommand %s not recognized", os.Args[1])
		os.Exit(1)
	}
}
