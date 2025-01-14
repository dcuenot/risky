formats
===========

Java routines for manipulating binary formatted files of vessel positions and static vessel data. 

The *BinaryFixes* format provides very good read rates from Java code, up to **9m** records per second from one SSD (and up to **30m** per second in parallel) which beats the hell out of decoding raw NMEA at ~300K records/second.

The *Netcdf* format is another binary format option to hold fixes.

BinaryFixes format (.track)
--------------------------------
All values use *Big Endian* bit order.

| Name         | Type | Bytes | Notes |
|:-------------|:-----|:-----|:-----|
| latitude | float | 4 
| longitude | float | 4
| time | signed long | 8 | ms since 1970-01-01 00:00 UTC
| latency | signed integer | 4 | unit is seconds, -1 = not present 
| source | signed short | 2 | 0 = not present, 1 = present but unknown, others TBA
| navigational status | signed byte | 1 | 127 = not present
| rate of turn | signed byte | 1 | -128 = not present, others TBA
| speed over ground | signed short | 2 |unit is 1/10 knot, 1023 = not present
| course over ground | signed short | 2 |unit is 1/10 degree, 3600 = not present
| heading | signed short | 2 | unit is 1/10 degrees, 3600 = not present
| ais class | byte | 1 | 0 = A, 1 = B
| **Total** | | 31 | |

How to use in java
---------------------

```java
Observable<Fix> fixes = BinaryFixes.from(new File("123.track"));
```

How to use with R
----------------------------
Note: the method below doesn't read the timestamp properly (R doesn't offer a 64-bit integer type), needs fixing.

To read the above binary format using the R language see [read-binary-fixes.r](src/test/resources/read-binary-fixes.r). 
To test, generate the sample files (see below), then:

```bash
Rscript src/test/resources/read-binary-fixes.r
```

Generate sample files
------------------------
Run unit tests:
```
mvn test
```

This generates these files in the *target* directory:
* ```123456789.track``` - 100,000 identical fixes
* ```123456790.track``` - 2 fixes with different positions and times
* ```test.nc``` - 2 fixes with different positions and times in Netcdf format

How to print the contents of .track in human readable form to stdout
----------------------------------------------------------------------
```bash
mvn compile exec:java -Pprint -Dfile=<FILENAME>
```

How to convert an NMEA stream to BinaryFixes
--------------------------------------------
You need the *ais* dependency for this:

```java
import au.gov.amsa.ais.rx.Streams;

Observable<String> nmea = ...
Observable<Fix> fixes = Streams.extractFixes(nmea);
```

How to downsample 
---------------------
The code below downsamples a stream of fixes with for the same vessel that must be in ascending time order so that the minimum time between fixes is 5 minutes.

```java
import au.gov.amsa.risky.format.Downsample;
import java.util.concurrent.TimeUnit;

Observable<Fix> fixes = BinaryFixes.from(new File("123.track"));
Observable<Fix> sampled = fixes.compose(Downsample.minTimeStep(5, TimeUnit.MINUTES));
```

Given a directory with nested .track files you can make a downsampled copy (interval of 5 minutes) using the following command:

```bash
cd formats
mvn compile exec:java -Pdownsample -Dinput=<INPUT_DIRECTORY> -Doutput=<OUTPUT_DIRECTORY> -Dpattern=".*.track" -Dms=300000
```

How to sort
-----------------------
To sort a set of .track files in a directory (in-situ):

```bash
mvn compile exec:java -Psort -Doutput=/media/an/binary-fixes
```

How to convert to netcdf
---------------------------
To convert a directory structure of ```.track``` or ```.track.gz``` files (BinaryFixes) to ```.nc``` (NetCDF) format, here is an example:

```bash
cd formats
mvn compile exec:java -Pnc -Dinput=<INPUT_DIRECTORY> -Doutput=<OUTPUT_DIRECTORY> -Dpattern=".*.track"
```

The output directory will have the same filenames but will have extension ```.nc``` instead of ```.track```.

Not that this command also handles zipped track files by autodetecting the extension ```.gz```. Just remember to adjust the pattern to look for say ```.*.track.gz``` files.

How to zip all .track or .nc files in directory structure
----------------------------------------------------------------
To convert a directory structure of ```.track``` files (BinaryFixes) to ```.track.zip``` GZipped format, here is an example:

```bash
cd formats
mvn compile exec:java -Pzip -Dinput=<INPUT_DIRECTORY> -Doutput=<OUTPUT_DIRECTORY> -Dpattern=".*.track"
```

The output directory will have the same filenames suffixed with ```.zip```.

How to process many files concurrently
--------------------------------------
Given that one of the file arrangements being used is one BinaryFix file 
per month per vessel, this fact can be used to leverage concurrency.

This example uses concurrency to process many BinaryFix files at a time
up to the number of available processors minus one (just to leave a bit
of processing power for downstream). 

```java
// using concurrency, count all the fixes across all files in the '2014'
// directory
Observable<File> files = Observable.from(Files.find(new File("2014"),
				Pattern.compile("\\d+\\.track")));
int count = files
    // group the files against each processor
    .buffer(Runtime.getRuntime().availableProcessors() - 1)
    // do the work per buffer on a separate scheduler
   .flatMap(list -> Observable.from(list)
		// count the fixes in each file
		.flatMap(countFixes())
		// perform concurrently
		.subscribeOn(Schedulers.computation()))
    // total all the counts
    .reduce(0, (a,b) -> a + b)
    // block and get the result
    .toBlocking().single();
System.out.println("total fixes = " + count);
```

Performance
--------------
Using Intel Xeon CPU ES-1650 @ 3.2GHz and SSD, binary format is read in at up to **9m records per second** single-threaded.
This compares very favourably with NMEA decode which is about 300K records/second (probably lots worse because the decode does extractions lazily).

Characteristics
-------------------
| Metric         | NMEA | BinaryFixes 
|:---------------|:-----|:-----|
| GZ compression factor | 3.9 | 8
| Uncompressed disk space ratio | 2.8  | 1
| GZ compressed disk space ratio | 5 | 1

GZ compression yields 8x compression on the BinaryFixes format. For example: 

```802KB n.track -> 107KB n.track.gz```

Note that GZ compression of NMEA yields 3.9x compression.

GZ compressed disk space savings of BinaryFixes is better than NMEA by a factor of 5.  For example:

```167.9MB n.nmea.gz vs 29MB n.track.gz```

Uncompressed disk space saving is better than NMEA by a factor of 2.8. For example:

```654.7MB n.nmea vs 234MB n.track```
