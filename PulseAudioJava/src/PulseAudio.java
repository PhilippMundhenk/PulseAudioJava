import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

public class PulseAudio {
	public static final String module = "PulseAudio";
	public static final int ABORT_COUNTER_MAX = 500;
	public static final int MIN_TIME_BETWEEN_PA_ACCESS_MS = 0;
	private static long lastAccess = 0;
	private static Semaphore semaphorePulseAudio = new Semaphore(1);

	public static class Log {
		private static Boolean output = false;

		public static void setOutput(Boolean val) {
			output = val;
		}

		public static void log(String str) {
			if (output) {
				System.out.println(module + ": " + str);
			}
		}
	}

	public static class SinkInput {
		public Process playProcess;
		public Process getProcess;
		public int sinkInputID;
	}

	public enum Player {
		MPLAYER, XINE
	}

	/**
	 * This method limits the access to PulseAudio, by waiting a minimum time
	 * between two accesses. This is used to prevent crashes in PulseAudio. It
	 * needs to be called before any other action in PulseAudio.
	 */
	protected static synchronized void checkAccessTime() {
		if (lastAccess == 0) {
			lastAccess = System.currentTimeMillis();
			return;
		} else {
			while (System.currentTimeMillis() - lastAccess < MIN_TIME_BETWEEN_PA_ACCESS_MS)
				;
			lastAccess = System.currentTimeMillis();
			return;
		}
	}

	/**
	 * This method creates a PulseAudio null sink and returns the index of the
	 * created sink
	 * 
	 * @param sinkName
	 *            name of the sink to create
	 * 
	 * @return >0: index of the newly created null sink -1: not able to identify
	 *         created sink -2: sink not created
	 * @throws IOException
	 */
	public static int createSink(String sinkName) throws IOException {
		checkAccessTime();

		HashSet<Integer> indicesBefore = new HashSet<Integer>();
		HashSet<Integer> indicesAfter = new HashSet<Integer>();

		String scheme = " | grep index | sed 's/\\([ \t]*\\|[ \t]*\\*[ \t]*\\)index: //'";
		String cmd = "pacmd list-sinks" + scheme;

		/* reading in sink numbers */
		Process p = Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c", cmd });
		InputStream lsOut = p.getInputStream();
		InputStreamReader r = new InputStreamReader(lsOut);
		BufferedReader in = new BufferedReader(r);
		String line;
		while ((line = in.readLine()) != null) {
			indicesBefore.add(Integer.decode(line));
		}

		p = Runtime.getRuntime().exec(
				new String[] {
						"/bin/bash",
						"-c",
						"pactl load-module module-null-sink sink_name="
								+ sinkName });

		/*
		 * this is necessary to allow PulseAudio some CPU time to create the
		 * sink
		 */
		in.close();
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		/* reading in sink numbers */
		p = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", cmd });
		lsOut = p.getInputStream();
		r = new InputStreamReader(lsOut);
		in = new BufferedReader(r);
		while ((line = in.readLine()) != null) {
			indicesAfter.add(Integer.decode(line));
		}
		in.close();

		/* compare sink numbers before and after creating */
		indicesAfter.removeAll(indicesBefore);
		if (indicesAfter.size() > 1) {
			/* Error! Could not identify created sink! */
			Log.log("Error! Could not identify null sink!");
			return -1;
		} else if (indicesAfter.size() == 0) {
			/* Error! Sink could not be created */
			Log.log("Error! Could not create null sink!");
			return -2;
		} else {
			/* successfully created and identified stream */
			int index = (int) indicesAfter.toArray()[0];
			Log.log("created null sink " + sinkName + " at index " + index);
			return index;
		}
	}

	/**
	 * This method loads a stream from a given URL via the defined player and
	 * returns the handle to the player process, as well as the index of the
	 * created PulseAudio sink input
	 * 
	 * @param URL
	 *            address of the stream to open
	 * @param player
	 *            player to use to play stream
	 * @param sink
	 *            sink to play into (0: default sink), applicable for mplayer
	 *            only
	 * @return class containing the process handle and the index of the created
	 *         sink-input index for the created stream
	 * @throws IOException
	 */
	public static SinkInput createStream(String URL, Player player, int sink)
			throws IOException {
		checkAccessTime();

		HashSet<Integer> indicesBefore = new HashSet<Integer>();
		HashSet<Integer> indicesAfter = new HashSet<Integer>();
		SinkInput returnVal = new SinkInput();

		String scheme = " | grep index | sed 's/\\([ \t]*\\|[ \t]*\\*[ \t]*\\)index: //'";
		String cmd = "pacmd list-sink-inputs" + scheme;

		Process p = Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c", cmd });
		InputStream lsOut = p.getInputStream();
		InputStreamReader r = new InputStreamReader(lsOut);
		BufferedReader in = new BufferedReader(r);
		String line;
		Log.log("indices before:");
		while ((line = in.readLine()) != null) {
			indicesBefore.add(Integer.decode(line));
			Log.log(line);
		}

		if (player == Player.MPLAYER) {
			if (sink == 0) {
				returnVal.playProcess = Runtime.getRuntime().exec(
						new String[] {
								"/bin/bash",
								"-c",
								"/usr/bin/mplayer -ao pulse " + URL
										+ " > /dev/null  2>&1" });
			} else {
				returnVal.playProcess = Runtime.getRuntime().exec(
						new String[] {
								"/bin/bash",
								"-c",
								"/usr/bin/mplayer -ao pulse::" + sink + " "
										+ URL + " > /dev/null  2>&1" });
			}
		} else if (player == Player.XINE) {
			returnVal.playProcess = Runtime.getRuntime().exec(
					new String[] {
							"/bin/bash",
							"-c",
							"/usr/bin/aaxine -A pulseaudio " + URL
									+ " > /dev/null 2>&1" });
		}

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		int abortCounter = 0;

		while (indicesBefore.containsAll(indicesAfter)) {
			indicesAfter = new HashSet<>();
			p = Runtime.getRuntime().exec(
					new String[] { "/bin/bash", "-c", cmd });
			lsOut = p.getInputStream();
			r = new InputStreamReader(lsOut);
			in = new BufferedReader(r);
			while ((line = in.readLine()) != null) {
				indicesAfter.add(Integer.decode(line));
			}

			abortCounter++;
			if (abortCounter > ABORT_COUNTER_MAX) {
				break;
			}
		}

		Log.log("indices after:");
		for (Iterator<Integer> i = indicesAfter.iterator(); i.hasNext();) {
			Integer integer = (Integer) i.next();

			Log.log("" + integer);
		}

		in.close();
		r.close();
		lsOut.close();

		indicesAfter.removeAll(indicesBefore);
		if (indicesAfter.size() > 1) {
			/* Error! Could not identify created stream! */
			Log.log("Error! Could not identify stream sink (" + URL + ")!");
			returnVal.sinkInputID = -1;
			return returnVal;
		} else if (indicesAfter.size() == 0) {
			/* Error! Stream could not be created */
			Log.log("Error! Could not create stream (" + URL + ")!");
			returnVal.sinkInputID = -2;
			return returnVal;
		} else {
			/* successfully created and identified stream */
			returnVal.sinkInputID = (int) indicesAfter.toArray()[0];
			Log.log("created stream (" + URL + ") at sink " + sink
					+ ", sinkInput: " + returnVal.sinkInputID);
			return returnVal;
		}
	}

	/**
	 * This method sets the default sink for PulseAudio applications
	 * 
	 * @param sinkIndex
	 *            index of the sink to make default
	 * @throws IOException
	 */
	public static void setDefaultSink(int sinkIndex) throws IOException {
		checkAccessTime();

		Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c",
						"pacmd set-default-sink " + sinkIndex });
		Log.log("changed default sink to " + sinkIndex);
	}

	/**
	 * This method combines a set of given sinks
	 * 
	 * @param combinedSink
	 *            element containing combined sink parameters
	 * @param sinks
	 *            collection of sinks indices to combine
	 * @return index of newly created sink
	 * @throws IOException
	 */
	public static int combineSinks(CombinedSink combinedSink,
			Collection<Integer> sinks) throws IOException {
		checkAccessTime();

		HashSet<Integer> indicesBefore = new HashSet<Integer>();
		HashSet<Integer> indicesAfter = new HashSet<Integer>();

		String scheme = " | grep index | sed 's/\\([ \t]*\\|[ \t]*\\*[ \t]*\\)index: //'";
		String cmd = "pacmd list-sinks" + scheme;

		/* reading in sink numbers */
		Process p = Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c", cmd });
		InputStream lsOut = p.getInputStream();
		InputStreamReader r = new InputStreamReader(lsOut);
		BufferedReader in = new BufferedReader(r);
		String line;
		Log.log("indices before: ");
		while ((line = in.readLine()) != null) {
			indicesBefore.add(Integer.decode(line));
			Log.log(line);
		}

		String combindCmd = "pactl load-module module-combine-sink sink_name="
				+ combinedSink.name + " slaves=";
		Iterator<Integer> it = sinks.iterator();
		while (it.hasNext()) {
			int sinkNo = it.next();
			combindCmd = combindCmd.concat(Integer.toString(sinkNo));
			if (it.hasNext()) {
				combindCmd = combindCmd.concat(",");
			}
		}
		byte[] consoleInput = new byte[10];
		InputStream inStream = Runtime.getRuntime()
				.exec(new String[] { "/bin/bash", "-c", combindCmd })
				.getInputStream();
		int read = -1;
		while (read == -1) {
			read = inStream.read(consoleInput);
		}
		int moduleNumber = Integer
				.decode(new String(consoleInput, 0, read - 1));
		combinedSink.moduleNumber = moduleNumber;
		Log.log("module number of sink: " + moduleNumber);

		in.close();

		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		/* reading in sink numbers */
		p = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", cmd });
		lsOut = p.getInputStream();
		r = new InputStreamReader(lsOut);
		in = new BufferedReader(r);
		Log.log("indices after: ");
		while ((line = in.readLine()) != null) {
			indicesAfter.add(Integer.decode(line));
			Log.log(line);
		}
		in.close();

		/* compare sink numbers before and after creating */
		indicesAfter.removeAll(indicesBefore);
		if (indicesAfter.size() > 1) {
			/* Error! Could not identify created sink! */
			Log.log("Error! Could not identify combined sink!");
			return -1;
		} else if (indicesAfter.size() == 0) {
			/* Error! Sink could not be created */
			Log.log("Error! Could not create combined sink!");
			return -2;
		} else {
			/* successfully created and identified stream */
			int index = (int) indicesAfter.toArray()[0];
			Log.log("created combined sink " + combinedSink.name + " at index "
					+ index);
			return index;
		}
	}

	/**
	 * This method finds the sink for a given sinkInput
	 * 
	 * @param sinInput
	 *            sinkInput to find sink for
	 * @return index of sink receiving sinkInput
	 * @throws IOException
	 */
	public static int getSinkIndex(int sinkInput) throws IOException {
		checkAccessTime();

		String scheme = " | grep -A 4 \"index: "
				+ sinkInput
				+ "\" | grep \"sink\" | sed 's/[ \t]*sink: //' | sed 's/[ \t].*//'";
		String cmd1 = "pacmd list-sink-inputs" + scheme;
		Process p = Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c", cmd1 });

		InputStream lsOut = p.getInputStream();
		InputStreamReader r = new InputStreamReader(lsOut);
		BufferedReader in = new BufferedReader(r);

		String line;
		while ((line = in.readLine()) != null) {
			return Integer.decode(line);
		}

		return -1;
	}

	/**
	 * This method moves the given sink input to the given destination sink
	 * 
	 * @param sinkInput
	 *            sinkInput to move
	 * @param destinationSink
	 *            destination sink to move sink input to
	 * @throws IOException
	 */
	public static void moveSinkInput(int sinkInput, int destinationSink)
			throws IOException {
		checkAccessTime();

		Runtime.getRuntime().exec(
				new String[] {
						"/bin/bash",
						"-c",
						"pactl move-sink-input " + sinkInput + " "
								+ destinationSink });
		Log.log("moved sink input " + sinkInput + " to sink " + destinationSink);
	}

	/**
	 * This method sets the volume of the given sink
	 * 
	 * @param sink
	 *            sink to adjust volume
	 * @param volume_percent
	 *            volume to set in percent
	 * @throws IOException
	 */
	public static void setVolume(int sink, int volume_percent)
			throws IOException {
		checkAccessTime();

		int volume = 65535 / 100 * volume_percent;
		Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c",
						"pacmd set-sink-volume " + sink + " " + volume });
		Log.log("set volume of sink " + sink + " to " + volume + " ("
				+ volume_percent + "%)");
	}

	/**
	 * This method returns the sink index to a given deviceIdentifier
	 * 
	 * @param devicePath
	 *            sysfs.path of the device
	 * @return index of the device to find
	 */
	public static int getIndex(String devicePath) {
		checkAccessTime();

		try {
			String scheme = " | grep -B 42 \""
					+ devicePath
					+ "\" | grep \"index\" | sed 's/\\([ \t]*\\|[ \t]*\\*[ \t]*\\)index: //'";
			String cmd1 = "pacmd list-sinks" + scheme;
			Process p = Runtime.getRuntime().exec(
					new String[] { "/bin/bash", "-c", cmd1 });

			InputStream lsOut = p.getInputStream();
			InputStreamReader r = new InputStreamReader(lsOut);
			BufferedReader in = new BufferedReader(r);

			String line;
			while ((line = in.readLine()) != null) {
				return Integer.decode(line);
			}

			return -1;
		} catch (IOException e) {
			e.printStackTrace();

			return -1;
		}
	}

	/**
	 * This method starts a stream via wget
	 * 
	 * @param wgetURL
	 *            URL of stream to open via wget
	 * @param sink
	 *            sink to attach stream to
	 * @return stream information of created stream
	 * @throws IOException
	 */
	public static SinkInput startMusicStream(String wgetURL, Integer sink,
			String pipePathFull) throws IOException {
		checkAccessTime();

		HashSet<Integer> indicesBefore = new HashSet<Integer>();
		HashSet<Integer> indicesAfter = new HashSet<Integer>();
		SinkInput returnVal = new SinkInput();

		String scheme = " | grep index | sed 's/\\([ \t]*\\|[ \t]*\\*[ \t]*\\)index: //'";
		String cmd = "pacmd list-sink-inputs" + scheme;

		Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c", "rm " + pipePathFull });
		Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c", "mkfifo " + pipePathFull });

		Process p = Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c", cmd });
		InputStream lsOut = p.getInputStream();
		InputStreamReader r = new InputStreamReader(lsOut);
		BufferedReader in = new BufferedReader(r);
		String line;
		while ((line = in.readLine()) != null) {
			indicesBefore.add(Integer.decode(line));
		}
		in.close();
		Log.log("starting WGET process for stream " + wgetURL);
		returnVal.getProcess = Runtime.getRuntime().exec(
				new String[] { "/bin/bash", "-c",
						"wget \"" + wgetURL + "\" -O - > " + pipePathFull });
		Log.log("completed starting WGET process for stream " + wgetURL);

		Log.log("starting play process for stream " + wgetURL + " from pipe "
				+ pipePathFull + " on sink " + sink);
		returnVal.playProcess = Runtime.getRuntime().exec(
				new String[] {
						"/bin/bash",
						"-c",
						"mplayer -ao pulse::" + sink + " -cache 2048 "
								+ pipePathFull + " > /dev/null 2>&1" });
		Log.log("completed starting play process for stream " + wgetURL
				+ " from pipe " + pipePathFull + " on sink " + sink);

		while (indicesBefore.containsAll(indicesAfter)) {
			indicesAfter = new HashSet<>();
			p = Runtime.getRuntime().exec(
					new String[] { "/bin/bash", "-c", cmd });
			lsOut = p.getInputStream();
			r = new InputStreamReader(lsOut);
			in = new BufferedReader(r);
			while ((line = in.readLine()) != null) {
				indicesAfter.add(Integer.decode(line));
			}
		}
		in.close();

		indicesAfter.removeAll(indicesBefore);
		if (indicesAfter.size() > 1) {
			/* Error! Could not identify created stream! */
			Log.log("Error! Could not identify stream sink (" + wgetURL + ")!");
			returnVal.sinkInputID = -1;
			return returnVal;
		} else if (indicesAfter.size() == 0) {
			/* Error! Stream could not be created */
			Log.log("Error! Could not create stream (" + wgetURL + ")!");
			returnVal.sinkInputID = -2;
			return returnVal;
		} else {
			/* successfully created and identified stream */
			returnVal.sinkInputID = (int) indicesAfter.toArray()[0];
			Log.log("created stream (" + wgetURL + ") at sink " + sink
					+ ", sinkInput: " + returnVal.sinkInputID);
			return returnVal;
		}
	}

	/**
	 * This method checks if a sink exists, which includes the given
	 * sinkNamePart.
	 * 
	 * @param sinkNamePart
	 *            full or part of the name of the sink to check
	 * 
	 * @return true: sink with this part in the name exists false: sink does not
	 *         exist
	 */
	public static Boolean checkIfSinkExists(String sinkNamePart) {
		Boolean sinkExists = false;

		try {
			String cmd = "pacmd list-sinks | grep \"name: \"";
			Process p = Runtime.getRuntime().exec(
					new String[] { "/bin/bash", "-c", cmd });
			InputStream lsOut = p.getInputStream();
			InputStreamReader r = new InputStreamReader(lsOut);
			BufferedReader in = new BufferedReader(r);
			String line;
			while ((line = in.readLine()) != null) {
				if (line.contains(sinkNamePart)) {
					sinkExists = true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return sinkExists;
	}

	/**
	 * This method ensures that only one instance accesses PulseAudio. It also
	 * ensures that timing requirements are kept.
	 */
	public static void acquireSemaphore() throws InterruptedException {
		checkAccessTime();
		semaphorePulseAudio.acquire();
	}

	/**
	 * This method ensures that only one instance accesses PulseAudio. It also
	 * ensures that timing requirements are kept.
	 */
	public static void releaseSemaphore() throws InterruptedException {
		lastAccess = System.currentTimeMillis();
		semaphorePulseAudio.release();
	}

	/**
	 * This method deletes a combined sink from the list of sinks.
	 * 
	 * @param sink
	 *            element containing combined sink parameters
	 */
	public static void removeSink(CombinedSink sink) {
		try {
			Log.log("removing unused sink (module number: " + sink.moduleNumber
					+ ")");
			Runtime.getRuntime().exec(
					new String[] { "/bin/bash", "-c",
							"pactl unload-module " + sink.moduleNumber });
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method removes an input from a sink.
	 * 
	 * @param sinkInputID
	 *            ID of the sink input to remove
	 */
	public static void removeSinkInput(Integer sinkInputID) {
		try {
			Log.log("removing sinkInputID " + sinkInputID + " from sink");
			Runtime.getRuntime().exec(
					new String[] { "/bin/bash", "-c",
							"pacmd kill-sink-input " + sinkInputID });
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
