#include <string>
#include <map>

struct Sequencer {
  std::string lock_name;
  uint32_t seq_num;
  std::string key;
  time_t expiration;
};

class ILockContainer {
  public:
    /**
     * A method that adds a lock to the container
     * returns false if the lock already exists or otherwise couldn't be added
     */
    virtual bool AddLock(const std::string& lock_name) = 0;

    virtual bool RemoveLock(const std::string& lock_name) = 0;

    virtual Sequencer GetSequencer(const std::string& lock_name) = 0;

    virtual void GetLock(const Sequencer& sequencer) = 0;
};