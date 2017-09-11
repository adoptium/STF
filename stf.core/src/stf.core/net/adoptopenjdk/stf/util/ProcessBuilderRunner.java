/*******************************************************************************
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

package net.adoptopenjdk.stf.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;


/**
 * This is a utility class to help with running processes from Java.
 * As with ProcessBuilder, an instance of this class can be run multiple times.
 *  
 * It does the mundane jobs of consuming the output and error streams.
 * Not consuming these streams can lead to process hangs or missed information about errors.
 * This class also avoids common bugs around the visibility of output captured by the stream handling threads.
 * 
 * Note: Holds stdout and stderr output in StringBuilder, so don't use this class if these are going to be too big.  
 */
public class ProcessBuilderRunner {
	private final static long HEARTBEAT = 10*1000;
    ProcessBuilder pb;
	
    StreamConsumer stdOutConsumer;
    StreamConsumer stdErrConsumer;	
	

    public ProcessBuilderRunner(ProcessBuilder pb) {
        this.pb = pb;
    }

    /**
     * Starts the process and waits for it's completion.
     * 
     * @return int which holds the process exit value.
     * @throws any exceptions encountered during stdout or stderr stream consumption.
     */
    public int execute() throws Exception {
        Process process = pb.start();

        // Start threads to consume the stdout and stderr output
		stdOutConsumer = new StreamConsumer("P4 OUT> ", process.getInputStream());
		stdErrConsumer = new StreamConsumer("P4 ERR> ", process.getErrorStream());
		stdOutConsumer.start();
		stdErrConsumer.start();
		
		// Wait for the process and the consumers to finish
		int exitValue = process.waitFor();
		stdOutConsumer.join();
		stdErrConsumer.join();
		
		// If any of the consumer threads had an exception, then rethrow
		stdOutConsumer.throwAnyCaughtExceptions();
		stdErrConsumer.throwAnyCaughtExceptions();
		
		return exitValue;
    }

       
    public ArrayList<String> getStdOut() {
        return stdOutConsumer.getCapturedTextList();
    }
	
    public String getStdOutText() {
    	return stdOutConsumer.getCapturedText();
    }
    
    public ArrayList<String> getStdErr() {
        return stdErrConsumer.getCapturedTextList();
    }
    
    public String getStdErrText() {
        return stdErrConsumer.getCapturedText();
    }
	
	
    /** 
     * Inner class for running thread to consume process output.
     * Prevents deadlocks and hangs.
     */
    private class StreamConsumer extends Thread {
        private InputStream in;
        private ArrayList<String> capturedText = new ArrayList<String>();
        @SuppressWarnings("unused")
		private final String prefix;
        
        private volatile Exception caughtException = null;
        
        
        StreamConsumer(String prefix,  InputStream in) {
            this.in = in;
            this.prefix = prefix;
        }
 
		@Override
        public void run() {
			long lastTS = System.currentTimeMillis();
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(in));
                String line = null;
                while ((line = br.readLine()) != null) {
                	//System.out.println(prefix+line);
                    capturedText.add(line);
                    if (System.currentTimeMillis()-lastTS>HEARTBEAT)
                    {
                    	//System.out.println(prefix+"Update: Read "+capturedText.size()+" lines");
                    	lastTS = System.currentTimeMillis();
                    }
                }
            } catch (IOException e) {
            	caughtException = e;
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    caughtException = e;
                }
            }
        }
        
		public synchronized ArrayList<String> getCapturedTextList() {
			return capturedText;
		}
		
		public synchronized String getCapturedText() { 
			StringBuilder buff = new StringBuilder();
			
			for (String line : capturedText) {
				buff.append(line);
				buff.append("\n");
			}
			
            return buff.toString();
        }
        
        void throwAnyCaughtExceptions() throws Exception { 
            if (caughtException != null) { 
                throw caughtException;
            }
        }
    }


    /**
     * Utility method for describing a list of command line arguments.
     * @param commands is the command arguments to be passed to processBuilder.
     * @return A formatted string describing the commands to be run.
     */
	public static String describeCommand(String... commands) {
        StringBuilder commandDescription = new StringBuilder();
        for (String commandComponent : commands) { 
            commandDescription.append(commandComponent);
            commandDescription.append(" ");
        }

        return commandDescription.toString();
	}
}