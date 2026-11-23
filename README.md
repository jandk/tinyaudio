# tinyaudio

[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/jandk/tinyaudio/maven.yml?logo=github)](https://github.com/jandk/tinyaudio/actions/workflows/maven.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/be.twofold/tinyaudio-core?logo=apachemaven)](https://central.sonatype.com/artifact/be.twofold/tinyaudio-core)
[![License](https://img.shields.io/github/license/jandk/tinyaudio)](https://opensource.org/licenses/MIT)

## Introduction

TinyAudio decodes FLAC, Vorbis and Opus. It's written in pure Java (11+), without any dependencies and without anything
native to ship, with a focus on accuracy.

Another one spun off from [Valen](https://github.com/jandk/valen), like [TinyBCDec](https://github.com/jandk/tinybcdec)
before it. Games ship their audio as Ogg Vorbis and Opus, and I wanted to play it without converting it first. Java
Sound
reads WAV, AIFF and AU, and that's about it. So, as is tradition, I wrote my own.

## What does it do?

| Codec  | Container   | Extension       | Support                                          |
|--------|-------------|-----------------|--------------------------------------------------|
| FLAC   | Native, Ogg | `.flac`, `.oga` | ✅ Up to 24 bits per sample                      |
| Vorbis | Ogg         | `.ogg`          | ✅ Vorbis I, so all of it                        |
| Opus   | Ogg         | `.opus`         | 🟡 CELT only, but including multichannel streams |

✅ Supported · 🟡 Partial

The format is detected from the data, not from the file name. A FLAC file called `.mp3` opens just fine. An actual MP3
does not.

## Installation

Every codec lives in its own module, so you only ship what you use. Pick the ones you need, the core and Ogg modules
come along by themselves.

```xml
<dependency>
    <groupId>be.twofold</groupId>
    <artifactId>tinyaudio-flac</artifactId>
    <version>0.1</version>
</dependency>
<dependency>
<groupId>be.twofold</groupId>
<artifactId>tinyaudio-opus</artifactId>
<version>0.1</version>
</dependency>
<dependency>
<groupId>be.twofold</groupId>
<artifactId>tinyaudio-vorbis</artifactId>
<version>0.1</version>
</dependency>
```

There is nothing to register. Codecs are found with `ServiceLoader`, so if the jar is there, the format opens.

## Usage

There are two ways in. The `AudioDecoder` API gives you the samples, Java Sound gives you a stream of bytes.

### AudioDecoder

`AudioDecoder.open` takes a `Path` or an `InputStream`, and finds the right decoder. The decoder owns the stream, so
closing one closes the other.

```java
try(AudioDecoder decoder = AudioDecoder.open(Path.of("music.flac"))){
AudioInfo info = decoder.info();
float[][] pcm = new float[info.channels()][4096];

    while(true){
int frames = decoder.read(pcm, 0, 4096);
        if(frames< 0){
    break;
    }
    // pcm[channel][0 .. frames] has your samples
    }
    }
```

`AudioInfo` has the sample rate, the channel count and the length in frames. A frame is one sample for every channel,
so one second is `sampleRate()` frames. FLAC usually knows its length up front, Ogg only knows it at the very end, so
there it's `-1`.

Decoders are **not thread-safe**: each thread needs its own.

### Java Sound

If you'd rather not deal with any of that, the core module registers itself with Java Sound. `AudioSystem` then opens
every supported format as 16-bit PCM, and you treat it like any WAV file.

```java
try(AudioInputStream in = AudioSystem.getAudioInputStream(new File("music.opus"))){
    // 16-bit signed little endian, ready for a SourceDataLine
    }
```

Passing an `InputStream` works too, as long as it supports mark and reset. Java Sound has always wanted that, so wrap it
in a `BufferedInputStream` if needed.

## Output format

Samples are planar: one array per channel. They come in two flavors, `read(float[][], ...)` and
`read(short[][], ...)`, which share their position, so mixing them is fine.

Float is the full precision output, with full scale at -1.0 to +1.0.

- FLAC is divided by a power of two, so nothing is lost. Multiply by `2^(bits - 1)` to get the original samples back.
- Vorbis and Opus come out as decoded, which can overshoot full scale a little. Nothing is clipped.

Short is converted from the float output: scaled by 32768, rounded and clamped.

- FLAC up to 16 bits comes out exactly. Anything higher is rounded to 16 bits.
- Opus is soft clipped first, just like libopus does for its 16-bit output.

Channels come in the order of the codec, which is not the same everywhere. Thanks, history.

- FLAC uses WAVE order. For 5.1: front left, front right, center, LFE, back left, back right.
- Vorbis and Opus use Vorbis order. For 5.1: front left, center, front right, back left, back right, LFE.

## Errors

Everything that goes wrong is an `IOException`, and the ones about the data itself get their own type:

- `UnsupportedFormatException`: the data is fine, I just don't support it. Either no decoder recognized the stream, or
  it uses a feature that's not implemented, like SILK in Opus.
- `AudioFormatException`: the data is broken. It's also the parent of the one above, so catch this one for both.

## What's not there

- Opus: SILK and hybrid packets. Only CELT is supported, as that's what the games I care about use. Music at common
  bitrates usually is too, speech and low bitrates are where SILK shows up. Those packets throw an
  `UnsupportedFormatException` when they're reached. SILK will not be ported. If I ever need it, it's going to be native
  libopus, against my will.
- Opus: packet loss concealment. These are files on disk, nothing gets lost.
- FLAC: more than 24 bits per sample, which is the limit of the FLAC subset anyway. Same for streams that change their
  sample rate, channel count or bit depth halfway through, or that have no STREAMINFO block.
- Ogg: chained files. Only the first logical stream is decoded.
- All formats: seeking.
- All formats: metadata, like tags or cover art. It is parsed right now, there's just no API for it yet.

## Accuracy

Accuracy was the whole point, so every codec is tested against its reference decoder, using the official test files
where there are any.

FLAC is the easy one, being lossless. Every file in
the [IETF test files](https://github.com/ietf-wg-cellar/flac-test-files)
carries an MD5 of its audio, and the output has to match it. All 64 subset files do, and so do the 4 uncommon ones with
features I support.

Opus is tested on the [RFC 8251 test vectors](https://opus-codec.org/testvectors/). Three of those are CELT only (01,
07 and 11), and those match the reference within 1 LSB. On top of that, the range decoder has to end up in the same
state after every single packet, which is about as strict as it gets. There are also Ogg Opus files in mono, stereo,
5.1, 7.1 and one that clips, against the output of opusdec, also within 1 LSB.

Vorbis has no MD5, so it's compared with libvorbis itself, sample for sample, over the
[Xiph test vectors](https://people.xiph.org/~xiphmont/test-vectors/vorbis/). All 29 files match within 1e-6. Well, on
Windows. The libvorbis from the Linux package managers is a bit further off, and seems to be built with `-ffast-math`,
so on Linux the tests allow 1e-5.

## Building

Requires JDK 11 or newer. Clone with submodules, as the FLAC test files live under `test-vectors/`:

```shell
git clone --recurse-submodules https://github.com/jandk/tinyaudio.git
```

The Opus and Vorbis test vectors are downloaded separately, which needs bash, wget and sha256sum:

```shell
bash scripts/opus-vectors.sh
bash scripts/vorbis-vectors.sh
```

Then build and test everything with:

```shell
mvn verify
```

The Vorbis tests need libvorbis to compare with. It comes with the repo for Windows, on Linux install it with your
package manager (`libvorbis0a` on Debian and Ubuntu). Or add `-DskipITs` to skip all of the above.

The `tinyaudio-app` module builds a tiny command line player, mostly to hear that things work:

```shell
java -jar tinyaudio-app/target/tinyaudio-app-0.1-SNAPSHOT.jar music.flac
```

## Todo

- The same channel order for every codec, instead of whatever the codec likes.
- The channel layout in `AudioInfo`, for FLAC files with a channel mask.
- Seeking.
- An API for the metadata.
