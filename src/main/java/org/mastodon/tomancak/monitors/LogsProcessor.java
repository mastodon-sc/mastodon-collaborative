/*-
 * #%L
 * mastodon-collaborative
 * %%
 * Copyright (C) 2020 - 2024 Vladimir Ulman
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mastodon.tomancak.monitors;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Iterator;
import de.mpicbg.ulman.inputParsers.AbstractParser;
import de.mpicbg.ulman.ShowLogsCompacted;
import de.mpicbg.ulman.outputPresenters.Presenter;
import de.mpicbg.ulman.outputPresenters.HTML;

public class LogsProcessor
{
	public
	LogsProcessor(final Map<String, Map<Long,Long>> stats)
	{
		if (stats == null)
			throw new RuntimeException("Cannot be instantiated with stats data = null");
		this.stats = stats;
		parser = new InputParser();
	}

	/** reference on the actual data: maps Users to their Times-to-Progress maps */
	final Map<String, Map<Long,Long>> stats;

	/** specialized parser that reads this.stats for the LogsProcessor */
	final InputParser parser;

	/** governs marshaling of the data the way LogsProcessor requires */
	class InputParser
	extends AbstractParser
	{
		public InputParser()
		{
			restartParsing();
		}

		public
		void restartParsing()
		{
			//NB: stats != null hopefully from the outter c'tor of LogsProcessor
			usersIt = stats.keySet().iterator();
			timeIt = null;
			moveToNextDatum();
		}

		void moveToNextDatum()
		{
			//if there is no time iterator yet or there is one at the end of its journey,
			//and we have next user to examine, we move on the first item of the next user
			while ((timeIt == null || !timeIt.hasNext()) && usersIt.hasNext())
			{
				nextUser = usersIt.next();
				timeIt = stats.get( nextUser ).keySet().iterator();
			}

			if (timeIt != null && timeIt.hasNext())
			{
				nextTime = timeIt.next();
				isThereNext = true;
			}
			else
				//usersIt.hasNext() == false
				isThereNext = false;
		}

		String nextUser;
		long nextTime;

		@Override
		protected
		void readNextXYMsg()
		{
			//just don't do anything if we were not supposed to be executed at all
			if (!isThereNext) return;

			//1. setup the current data from the current timeIt,usersIt
			currentEvent.x = nextUser;
			currentEvent.y = nextTime / getTypicalTimeResolution();
			currentEvent.msg.clear();
			currentEvent.msg.add("reached "+stats.get(nextUser).get(nextTime)
				+" on "+ LocalDateTime.ofEpochSecond(nextTime,0, ZoneOffset.UTC).toString());

			//2. try to move on the next piece of data
			//3. signal if the move was possible
			moveToNextDatum();
		}

		Iterator<String> usersIt;
		Iterator<Long> timeIt;

		@Override
		public
		long getTypicalTimeResolution()
		{ return 3600; }
	}


	/** outputs to HTML file the current content of this.stats */
	void writeHtmlTableFile(final Path htmlFile)
	{
		try {
			final int columnWidth = 40;

			parser.restartParsing();
			final Presenter presenter = new HTML(htmlFile.toFile(), columnWidth, 2);

			final ShowLogsCompacted writer = new ShowLogsCompacted(parser,presenter,1);
			//NB: this.parser.readNextXYMsg() already "granularizes" time stamps, so we set yTimeStep = 1
			writer.msgWrap = columnWidth;
			writer.process();
		} catch (IOException e) {
			System.out.println("Error producing output HTML file:");
			e.printStackTrace();
		}
	}
}
