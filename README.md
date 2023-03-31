# Vault2 - Abstraction Library for Bukkit - [![Build Status](https://app.travis-ci.com/MilkBowl/Vault.svg?branch=master)](https://app.travis-ci.com/MilkBowl/Vault)

## For Developers:
Please see the [VaultAPI](https://github.com/anjoismysign/VaultAPI2) page for
information on developing with Vault's API. In the past, you would use the same
artifact as servers installed, but the API has now been split from the main
project and is under a different artifact name. Please make sure you accommodate
this change in your build process.

## Installing
Installing Vault is as simple as copying the provided "Vault.jar" to your
"<bukkit-install-dir>/plugins" directory, and the rest is automatic! If you
wish to perform configuration changes, this can be done via a configuration
file but should not be necessary in most cases. See the "Advanced
Configuration" section for more information.


## Why Vault?
I have no preference which library suits your plugin and development efforts
best. Really, I thought a central suite (rather...Vault) of solutions was the
proper avenue than focusing on a single category of plugin. That's where
the idea for Vault came into play.

So, what features do I _think_ you'll like the most?

* No need to include my source code in your plugin
  All of Vault is run in its own plugin, so all you need to do is obtain an
  instance of it! This simplifies issues with multiple plugins using the same
  namespaces. Just simply add Vault.jar to your download zip file!
* Broad range of supported plugins
  I wanted an abstraction layer not only for Economic plugins but also
  Permission plugins as well.
* Choice!
  That's half the fun of Bukkit! We get to choose what to use. More choice
  has never hurt developers, so here's to choice!


## Permissions
* vault.admin
  - Determines if a player should receive the update notices
  - (Not currently used)

## License
Copyright (C) 2011-2018 Morgan Humes <morgan@lanaddict.com>

Vault is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Vault is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
with Vault. If not, see <http://www.gnu.org/licenses/>.

## Building
Vault comes with all libraries needed to build from the current branch and
also comes with an Apache Ant build file (build.xml) and a Maven build file
(pom.xml). Maven is currently the preferred build method.


## Supported Plugins
Vault provides abstraction for the following categories. If
you have your own plugin that you believe should be supported, you'll need
to add your own connector within your plugin as Vault no longer maintains
new plugin connectors.

  - Permissions
  - Chat
  - Economy
