# Bare Launcher

A simple home screen for Fire TV

## Set up

1. On the TV, go to **Settings › Device & Software › About** and press OK on the TV's name 7 times. (On some devices the menu is called **My Fire TV** instead.)
2. Go back to **Device & Software › Developer Options** and turn on **ADB Debugging**.
3. Note the IP address in **Device & Software › About › Network**.
4. On the Mac, run `scripts/setup-tv.sh <ip>` and choose **Allow** on the TV.
5. In Home on Fire (it opens on the TV), turn on **Accessibility service**, then use **Choose target app** to pick **Bare Launcher**.

## Use

- **Menu** on an app: move, pin or hide it.
- **Long-press Home**: Amazon's home screen.
- **To update:** run the setup script again.
