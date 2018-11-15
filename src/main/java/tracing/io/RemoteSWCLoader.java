package tracing.io;

import java.io.BufferedReader;

import tracing.Tree;

/**
 * Importers downloading remote SWC files should extend this interface.
 * 
 * @author Tiago Ferreira
 */
public interface RemoteSWCLoader {

	public boolean isDatabaseAvailable();
	public String getReconstructionURL(String cellId);
	public BufferedReader getReader(final String cellId);
	public Tree getTree(final String cellId);

}
