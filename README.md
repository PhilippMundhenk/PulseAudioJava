# PulseAudioJava
This tool allows to control functions of PulseAudio from Java

## Usage
Import the PulseAudio.java class into your project and start controlling PulseAudio directly from Java. The following table shows a minimal overview over possible functions. For detailed descriptions see source comments.

Function Name  | Description
------------- | -------------
```createSink()``` | Creates a new sink with name *sinkName* and returns its ID
```createStream``` | Loads a stream from a given URL via any of the defined players and returns the handle to the player process, as well as the index of the created PulseAudio sink input
```setDefaultSink``` | sets the default sink that is used for playback
```combineSinks``` | Combines a set of given sinks into a new combined sink
```getSinkIndex``` | Finds the sink for a given sink input ID
```moveSinkInput``` | Mmoves the given sink input to the given destination sink
```setVolume``` | Sets the volume of the given sink
```getIndex``` | Returns the sink index to a given deviceIdentifier (e.g. soundcard)
```startMusicStream``` | Starts a stream via wget (piped through pipePathFull) on sink *sink*
```checkIfSinkExists``` | Checks if a sink exists, which includes the given *sinkNamePart*
```removeSink``` | Deletes a combined sink from the list of sinks
```removeSinkInput``` | removes an input from a sink

##ToDo
- Create example to show how this works
- Replace logging by clean logging