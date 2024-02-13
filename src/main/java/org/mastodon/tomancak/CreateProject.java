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
package org.mastodon.tomancak;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;

import org.mastodon.tomancak.net.FileTransfer;
import org.mastodon.tomancak.net.DatasetServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

@Plugin( type = Command.class, name = "Mastodon CreateRemoteProject space plugin" )
public class CreateProject extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter
	private PrefService prefService;


	// ----------------- network options -----------------
	@Parameter(label = "URL address of the remote monitor:",
		persistKey = "remoteMonitorURL")
	private String remoteMonitorURL = "setHereServerAddress:"+ DatasetServer.defaultPort;

	@Parameter(label = "New project name on the remote monitor:",
		persistKey = "projectName")
	private String projectName = "setHereProjectName";

	@Parameter(label = "Obfuscate the new project name:")
	private boolean secureURL = true;


	// ----------------- implementation -----------------
	@Override
	public void run()
	{
		//LoadEarlierProgress reads 'remoteMonitorURL' itsway... so we have to save thatway too
		prefService.put(LoadEarlierProgress.class,"remoteMonitorURL",remoteMonitorURL);
		prefService.put(LoadEarlierProgress.class,"projectName",projectName);

		try {
			remoteMonitorURL = FileTransfer.fixupURL(remoteMonitorURL);
			final URL url = new URL(remoteMonitorURL
				+ (secureURL ?  "/addSecret/" : "/add/")
				+ projectName);

			//now connect and read out the status
			final String result = new BufferedReader(new InputStreamReader( url.openStream() )).readLine();
			if (!result.startsWith("ERROR"))
			{
				projectName = result;
				logService.info("The project was registered with this name: "+projectName);
				logService.info("Created a project space here: " + remoteMonitorURL + "/" + projectName);
				//
				//the projectName could have been changed, we resave to be on the safe side
				this.saveInputs();
				prefService.put(LoadEarlierProgress.class,"projectName",projectName);
			}
			else
			{
				logService.warn("Server refused to create the project! Nothing new registered.");
			}

		} catch (MalformedURLException | UnknownHostException e) {
			logService.error("URL is probably wrong:"); e.printStackTrace();
		} catch (ConnectException e) {
			logService.error("Some connection error:"); e.printStackTrace();
		} catch (IOException e) {
			logService.error("Some other IO error:"); e.printStackTrace();
		}
	}
}
