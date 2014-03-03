# clj-analytics-mongodiffer

Webapp that for comparing daily data in a prod and staging Mongo databases,
showing what collections are missing or have different data for the given day
in the two environments.
It is assumed that each collection has one document per one `epoch_day`.

The code has been extracted from a data analytics project and is currently not
generally usable (relying on details such as 1 doc per day, the epoch day field,
collection names ending in `.day` etc.) I share it so that I can discuss it.

## Usage

### Requirements

[Leiningen](http://leiningen.org/) - f.ex. downloaded it to the working dir.

### Running

Start:

    ./lein ring server-headless    # via nohup or in screen to keep it running

Browse to [locahost:3000](http://locahost:3000/) and follow the instructions.
Observe the console for logs.

### REPL

To start it from the REPL, load `server.clj` and execute `(create-server)` to start it
at port `8081` and `(destroy-server)` to stop it.

## License

Copyright © 2013 Jakub Holy

Distributed under the Eclipse Public License, the same as Clojure.
