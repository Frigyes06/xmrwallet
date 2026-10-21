# External lib build

## Requirements:

1. Docker

2. `make` (sudo apt install make, little tricky to get it on Windows, https://stackoverflow.com/questions/32127524/how-to-install-and-use-make-in-windows)

3. Huge amount of RAM and free disk space

## Building:

1. Clone https://github.com/monero-project/monero repo.

2. Check out the latest release branch (example: `git checkout v0.18.5.4`.

3. Update submodules: `git submodule update --init --force`.

4. Then go to folder with xmrwallet repo, then external-libs. Here you need to create symbol link to `monero` folder:

Linux: `ln -s ~/monero ~/xmrwallet/external-libs/monero`

Windows: `mklink /D "C:\Users\<USERNAME>\xmrwallet\external-libs\monero" "C:\Users\<USERNAME>\monero"`

5. Start Docker and then run `make` in `external-libs` folder. It will fail at end on Windows, but if `wallet2_api.h` exists in `include` folder, the build was successful. 

### Container build dependencies

The Dockerfiles pair `debian:bullseye-20240513` with the June 1, 2024
Debian main, updates, and security snapshots. Update the base image and
repositories together: pointing a newer base image at an older snapshot
leaves installed packages such as `libc6` and `perl-base` newer than the
exact versions required by the development packages.

To check only the package installation, run this from `external-libs`:

```sh
docker build --target build-deps -f android64.Dockerfile .
```

The initial APT sources use HTTP because the minimal base has no CA bundle.
APT still verifies Debian signatures; `ca-certificates` is explicitly
installed for the subsequent HTTPS downloads.
