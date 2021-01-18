package server

import (
	"context"
	"fmt"
	"log"
	"net"

	pl "wiligsi.com/plump/v2/protos"

	"google.golang.org/grpc"
)

// Make the port a thing that you can pass in to the server
const (
	port               = ":50051"
	defaultBufferLimit = 300
)

type lock struct {
	name       string
	head       pl.Sequencer
	sequencers chan pl.Sequencer
	locked     bool
}

type server struct {
	pl.UnimplementedPlumpServer
}

// WARNING : Gonna need some mutexes around this
// Read is fine, but write is not
var locks map[string]lock

func (s *server) CreateLock(ctx context.Context, in *pl.CreateDestroyRequest) (*pl.CreateDestroyReply, error) {
	// Gonna need a write lock for this or a read write or something
	lockName := in.GetLockName()
	_, lockExists := locks[lockName]

	if lockExists {
		return &pl.CreateDestroyReply{Success: false, Message: fmt.Sprintf("A lock named '%v' already exists", lockName)}, nil
	}

	locks[lockName] = lock{name: lockName, sequencers: make(chan pl.Sequencer, defaultBufferLimit), locked: false}
	return &pl.CreateDestroyReply{Success: true, Message: fmt.Sprintf("Successfully created lock '%v'", lockName)}, nil
}

func (s *server) DestroyLock(ctx context.Context, in *pl.CreateDestroyRequest) (*pl.CreateDestroyReply, error) {
	lockName := in.GetLockName()
	dlock, lockExists := locks[lockName]

	if !lockExists {
		return &pl.CreateDestroyReply{Success: false, Message: fmt.Sprintf("A lock named '%v' does not exist", lockName)}, nil
	}

	close(dlock.sequencers)
	delete(locks, lockName)
	return &pl.CreateDestroyReply{Success: true, Message: fmt.Sprintf("Successfully deleted lock '%v'", lockName)}, nil
}

func (s *server) ListLocks(ctx context.Context, in *pl.ListRequest) (*pl.ListReply, error) {
	lockNames := make([]string, len(locks))

	i := 0
	for k := range locks {
		lockNames[i] = k
		i++
	}

	return &pl.ListReply{LockNames: lockNames}, nil
}

func StartServer() {
	locks = make(map[string]lock)
	log.Println("Listening on tcp port", port)
	lis, err := net.Listen("tcp", port)
	if err != nil {
		log.Fatalf("failed to listen on port: %v", err)
	}
	s := grpc.NewServer()

	pl.RegisterPlumpServer(s, &server{})
	if err := s.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
