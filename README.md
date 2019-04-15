# edisyn-beatstep

Edisyn service provider to support Arturia Beatstep

FIXME: description

## Installation

Download from https://github.com/danielappelt/edisyn-beatstep/releases.

## Usage

Java JRE version 9 or newer is needed to run the application.

To run the JAR

    java -jar edisyn-beatstep-0.1.0-standalone.jar

To run from source:

1. Install leiningen from https://leiningen.org
2. Clone this repository
3. `lein do clean, with-profile compile compile, run`

To create a JAR from source:

1. Follow 1. and 2. from above
3. `lein uberjar`

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

## License

Copyright © 2019 Daniel Appelt
Licensed under the Apache License version 2.0

This work is based on synthesizer patch editor [Edisyn](https://github.com/eclab/edisyn) which is included as a git subtree in [src/edisyn](src/edisyn). Edisyn is
Copyright 2017 by Sean Luke
Licensed under the Apache License version 2.0
