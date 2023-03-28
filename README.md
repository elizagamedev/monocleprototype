# monocle image prototype

## What is this?
This is a prototype for a custom image format/protocol to send 640x400
full-range YCbCr images with relatively low latency over BLE. This is intended
to be used with custom firmware for the [Brilliant
Monocle](https://docs.brilliantmonocle.com/).

## How do I test it?
This app contains a server component (to simulate the future custom Monocle
firmware) and a client component, and requires two Android devices to test. The
app itself provides usage instructions.

Note that the connection is only established after turning the client's screen
off, so it may take a few seconds after for images to start sending.

## Troubleshooting
Make sure you pair your two Android devices via bluetooth beforehand. Also, I
have run into issues where the devices seem to "forget" each other, and need to
be repaired in the Bluetooth settings. I suspect this is probably caused by MAC
address randomization.
