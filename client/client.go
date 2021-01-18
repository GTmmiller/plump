package client

import (
	"context"
	"log"
	"time"

	"google.golang.org/grpc"
	pl "wiligsi.com/plump/v2/protos"
)

const (
	address     = "localhost:50051"
	defaultName = "world"
)

var c pl.PlumpClient
var ctx context.Context

func StartClient() {
	var cancel context.CancelFunc
	// Set up the connection to the server
	conn, err := grpc.Dial(address, grpc.WithInsecure(), grpc.WithBlock())
	if err != nil {
		log.Fatalf("did not connect: %v", err)
	}
	defer conn.Close()
	c = pl.NewPlumpClient(conn)

	// Contact the server and print out its response
	// Pass in the flags here or just handle it here?
	// I think passing in the flags would be a better thing to try

	ctx, cancel = context.WithTimeout(context.Background(), time.Second)
	defer cancel()

	CreateLock("Dork")
	ListLocks()
	CreateLock("Dork")
	DestroyLock("Dork")
	ListLocks()
	DestroyLock("Dork")
}

func CreateLock(name string) {
	r, err := c.CreateLock(ctx, &pl.CreateDestroyRequest{LockName: name})

	if err != nil {
		log.Fatalf("could not create lock: %v", err)
	}
	log.Printf("Success: %v Message: %s", r.GetSuccess(), r.GetMessage())
}

func DestroyLock(name string) {
	r, err := c.DestroyLock(ctx, &pl.CreateDestroyRequest{LockName: name})

	if err != nil {
		log.Fatalf("could not destroy lock: %v", err)
	}
	log.Printf("Success: %v Message: %s", r.GetSuccess(), r.GetMessage())
}

func ListLocks() {
	r, err := c.ListLocks(ctx, &pl.ListRequest{})

	if err != nil {
		log.Fatalf("could not destroy lock: %v", err)
	}
	log.Printf("LockNames: %v", r.GetLockNames())
}
