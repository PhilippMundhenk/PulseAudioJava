# PulseAudioJava
This tool allows to control functions of PulseAudio from Java

## Usage
Import the PulseAudio.java class into your project and start controlling PulseAudio directly from Java. The following table shows a minimal overview over possible functions. For detailed descriptions see source comments.

Function Name  | Description
------------- | -------------
```int createSink(String sinkName)``` | Creates a new sink with name *sinkName* and returns its ID
SinkInput **createStream**(String URL, Player player, int sink) | Loads a stream from a given URL via any of the defined players and returns the handle to the player process, as well as the index of the created PulseAudio sink input
void **setDefaultSink**(int sinkIndex) | sets the default sink that is used for playback
int **combineSinks**(CombinedSink combinedSink, Collection<Integer> sinks) | Combines a set of given sinks into a new combined sink
int **getSinkIndex**(int sinkInput) | Finds the sink for a given sink input ID
void **moveSinkInput**(int sinkInput, int destinationSink) | Mmoves the given sink input to the given destination sink
void **setVolume**(int sink, int volume_percent) | Sets the volume of the given sink
int **getIndex**(String devicePath) | Returns the sink index to a given deviceIdentifier (e.g. soundcard)
SinkInput **startMusicStream**(String wgetURL, Integer sink, String pipePathFull) | Starts a stream via wget (piped through pipePathFull) on sink *sink*
Boolean **checkIfSinkExists**(String sinkNamePart) | Checks if a sink exists, which includes the given *sinkNamePart*
void **removeSink**(CombinedSink sink) | Deletes a combined sink from the list of sinks
void **removeSinkInput**(Integer sinkInputID) | removes an input from a sink

##ToDo
- Create example to show how this works
- Replace logging by clean logging