risky
=====

<a href="https://travis-ci.org/amsa-code/risky"><img src="https://travis-ci.org/amsa-code/risky.svg"/></a>

Tools for analyzing timestamped position data such as vessel position reports from AIS.

| Subproject         | Description |
|:-------------------|:------------|
| [streams](streams) | read and publish socket broadcasts of string streams (like AIS)
| [ais](ais) | parse nmea and ais messages

Status: *pre-alpha*

Maven site reports are [here](http://amsa-code.github.io/risky/index.html) including [javadoc](http://amsa-code.github.io/risky/apidocs/index.html).

How to build
----------------

```bash
cd <WORKSPACE>
git clone https://github.com/amsa-code/risky.git
cd risky
mvn clean install
```

How to release
---------------
For project maintainers only.

Until we start releasing to Maven Central this is the procedure:

```bash
./release.sh <VERSION>
```

To release a non-tagged snapshot version to the AMSA internal repository:

```bash
./release-snapshot.sh
```
