import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public class Main {
	public static void main(String[] args) {
		/* for full demo, set an actual file here */
		String mp3file = "/home/user/Downloads/test.mp3";
		PulseAudio.Log.setOutput(true);
		
		/* create two new null sinks (hint: use getIndex to use real outputs) */
		String nullSinkName1 = "nullSink1";
		String nullSinkName2 = "nullSink2";
		String nullSinkName3 = "nullSink3";
		int sinkID1 = 0;
		int sinkID2 = 0;
		int sinkID3 = 0;
		try {
			sinkID1 = PulseAudio.createSink(nullSinkName1);
			sinkID2 = PulseAudio.createSink(nullSinkName2);
			sinkID3 = PulseAudio.createSink(nullSinkName3);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (0 < sinkID1) {
			CombinedSink combinedSink = new CombinedSink();
			combinedSink.name = "combinedSink1";
			Set<Integer> sinks = new LinkedHashSet<Integer>();
			sinks.add(sinkID1);
			sinks.add(sinkID2);

			/* combine null sinks to play the same */
			try {
				combinedSink.combinedSinkID = PulseAudio.combineSinks(combinedSink, sinks);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			/* play a file on new combined sink */
			PulseAudio.SinkInput sinkInput = null;
			try {
				sinkInput = PulseAudio.createStream(mp3file, PulseAudio.Player.MPLAYER, combinedSink.combinedSinkID);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if(sinkInput.playProcess != null)
			{
				System.out.println(mp3file+" is playing");
			}
			
			/* move playing stream to third sink */
			try {
				PulseAudio.moveSinkInput(sinkInput.sinkInputID, sinkID3);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			/* turn it up! */
			try {
				PulseAudio.setVolume(sinkID3, 100);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			/* delete unused combined sink */
			PulseAudio.removeSink(combinedSink);
			
			/* remove input from null sink 3*/
			PulseAudio.removeSinkInput(sinkInput.sinkInputID);
		}
	}
}
