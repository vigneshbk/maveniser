package com.viggs.uitls;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class LibtoMaven {

	private static String NEXUS_URL;

	private static String MAVEN_CENTRAL_URL; 
	// private static String NEXUS_URL = "http://mavencentral.sonatype.com,"

	private static String NEXUS_SEARCH_KEY = "sha1";

	private static Properties props = new Properties();

	public static void main(String[] args) {

		if (args == null || args.length < 1) {
			System.out
					.println("Usage  LibtoMaven <<path to libs folder >>  <<mvn/ivy format output>> -DisProxied=false");
			System.exit(1);
		}
		String jarsFolder = args[0];
		
		if( !new File(jarsFolder).exists() ||  !new File(jarsFolder).isDirectory())
		{
			System.out.println("Lib folder path is not valid or is empty");
			System.exit(1);
		}
		
		
		try {
			props.load(LibtoMaven.class.getClassLoader().getResourceAsStream(
					"config.properties"));
		} catch (IOException e) {
			System.err.println("Missing properties config file");
			System.exit(1);
		} 
		
		NEXUS_URL = props.getProperty("NEXUS_URL","https://repository.sonatype.org/service/local/data_index");

		MAVEN_CENTRAL_URL = props.getProperty("MAVEN_CENTRAL_URL","http://search.maven.org/solrsearch/select?q=##&rows=2&wt=json");
		
		String outputFormat = "maven.pomformat";
		
		if(args.length>1 &&  args[1]!=null && "ivy".equalsIgnoreCase(args[1]) )
		{
			outputFormat = "ivy.pomformat";
		}
		
		MessageFormat formatter = new MessageFormat(props.getProperty(outputFormat));

		StringBuilder dependString = new StringBuilder("<dependencies>");

		List<String> allJars = LibtoMaven.readAllJars(jarsFolder);
		
		if( allJars.size()<1)
		{
			System.out.println("Lib folder path is not is empty no jars present");
			System.exit(1);
		}

		Collections.sort(allJars);

		StringBuilder unresolved = new StringBuilder();
		int unresolvedCount = 0;

		for (int i = 0; i < allJars.size(); i++) {
			String jarName = allJars.get(i);

			dependString.append(formatter.format(new String[] { jarName,
					jarName, jarName }));
			dependString.append("\n");
			JarDescriptor desc = LibtoMaven.resolveMaveDescriptor(jarsFolder, allJars
					.get(i));

			if (desc != null) {
				// System.out.println(allJars.get(i)+ " --> "+desc);
				dependString.append(formatter.format(new String[] {
						desc.getGroupId(), desc.getArtifactId(),
						desc.getVersion() }));
				dependString.append("\n");

			} else{
				unresolved.append(allJars.get(i)).append("\n");
				unresolvedCount++;
			}
				

		}

		dependString.append("</dependencies>");

		System.out
				.println("--------------------------------------------------------------------");

		System.out.println(dependString);

		System.out
				.println("--------------------------------------------------------------------");

		System.out.println("Unresolved Jar names \n" + unresolved.toString());

		System.out
				.println("--------------------------------------------------------------------");
		
		System.out.println("Summary : Total Jar files resolved : " + (allJars.size()-unresolvedCount) +" Total unresolved : " + unresolvedCount);

	}

	private static JarDescriptor resolveMaveDescriptor(String baseaFolder, String jarFileName) {
		JarDescriptor descriptor = null;

		// First try to resolve the Jar using HASH in nexus repo
		try {
			String shaHash = LibtoMaven.getSHA1(baseaFolder + jarFileName);

			String xmlResp = LibtoMaven.lookUpNexus(shaHash);

			descriptor = LibtoMaven.decodeDescriptor(xmlResp);

		} catch (Exception ignore) {
		}

		try {
			// If Nexus lookup failed to a REST Call to Maven central and try
			// to resolve the maven artifact
			if (descriptor == null) {
				descriptor = lookUpMavenCentral(jarFileName);
			}
		} catch (Exception ignore) {
		}

		return descriptor;
	}

	public static String getSHA1(String jarName) throws Exception {
		String sha = null;

		InputStream is = new FileInputStream(new File(jarName));
		byte resultBytez[] = DigestUtils.sha(IOUtils.toByteArray(is));

		sha = new String(Hex.encodeHex(resultBytez));

		return sha;

	}

	/**
	 * Takes a folder name (Absolute) and gets all the jar files in the entire
	 * folder. Absoulute path is returned
	 * 
	 * @param folderLocation
	 * @return
	 */
	public static List<String> readAllJars(String folderLocation) {

		List<String> jarList = new ArrayList<String>();
		if (folderLocation == null)
			return jarList;

		File baseFolder = new File(folderLocation);
		if (!baseFolder.isDirectory() || baseFolder.listFiles().length < 1)
			return jarList;
		for (int i = 0; i < baseFolder.listFiles().length; i++) {
			File temp = baseFolder.listFiles()[i];

			if (temp.getName().endsWith(".jar")) {
				jarList.add(temp.getName());
			}

		}

		return jarList;
	}

	private static String lookUpNexus(String shaHash) throws Exception {

		URL serverUrl = new URL(NEXUS_URL);

		WebConversation conversation = new WebConversation();

		applyWebProxy(conversation);

		WebRequest request = new GetMethodWebRequest(serverUrl, "");

		request.setHeaderField("accept", "application/json");

		request.setParameter(NEXUS_SEARCH_KEY, shaHash);

		WebResponse response = conversation.getResponse(request);

		return ((response != null && response.getResponseCode() == 200) ? response
				.getText() : null);
	}

	private static JarDescriptor decodeDescriptor(String xmlRestResp)
			throws Exception {

		JarDescriptor jardesc = null;

		JSONParser parser = new JSONParser();

		Object obj = parser.parse(xmlRestResp);

		JSONObject jsonObj = (JSONObject) obj;

		JSONArray artifacts = ((JSONArray) jsonObj.get("data"));

		if (artifacts != null && artifacts.size() > 0) {

			JSONObject firstArtifcat = (JSONObject) artifacts.get(0);

			// System.out.println("artifacts --> " + firstArtifcat);

			if (firstArtifcat != null) {
				jardesc = new JarDescriptor();
				jardesc.setArtifactId((String) firstArtifcat.get("artifactId"));
				jardesc.setGroupId((String) firstArtifcat.get("groupId"));
				jardesc.setVersion((String) firstArtifcat.get("version"));
				jardesc.setJarname(jardesc.getArtifactId());
			}
		}
		return jardesc;
	}

	public static JarDescriptor lookUpMavenCentral(String jarname)
			throws Exception {

		WebConversation conversation = new WebConversation();

		applyWebProxy(conversation);

		WebRequest request = new GetMethodWebRequest(
				MAVEN_CENTRAL_URL.replaceAll("##", jarname));

		request.setHeaderField("accept", "application/json");

		WebResponse response = conversation.getResponse(request);

		JarDescriptor jardesc = null;

		if (response != null && response.getResponseCode() == 200) {

			JSONParser parser = new JSONParser();

			Object obj = parser.parse(response.getText());

			JSONObject jsonObj = (JSONObject) obj;

			JSONArray artifacts = ((JSONArray) ((JSONObject) jsonObj
					.get("response")).get("docs"));

			if (artifacts != null && artifacts.size() > 0) {

				JSONObject firstArtifcat = (JSONObject) artifacts.get(0);

				// System.out.println("artifacts --> " + firstArtifcat);

				if (firstArtifcat != null) {
					jardesc = new JarDescriptor();
					jardesc.setArtifactId((String) firstArtifcat.get("a"));
					jardesc.setGroupId((String) firstArtifcat.get("g"));
					jardesc.setVersion((String) firstArtifcat
							.get("latestVersion"));
					jardesc.setJarname(jardesc.getArtifactId());
				}
			}
		}

		return jardesc;
	}

	private static void applyWebProxy(WebConversation conversation) {
		boolean isProxyEnable = System.getProperty("isProxied") != null;
		if (isProxyEnable) {

			String proxyHost = props.getProperty("proxy-url");

			String proxyPort = props.getProperty("proxy-port", "8080");

			String proxyUser = props.getProperty("proxy-user");

			int port = Integer.parseInt(proxyPort);

			if (proxyUser != null && proxyUser.trim().length() > 0) {
				conversation.setProxyServer(proxyHost, port, proxyUser,
						props.getProperty("proxy-pass"));
			} else {
				conversation.setProxyServer(proxyHost, port);
			}
		}

	}

	public static class JarDescriptor {

		public JarDescriptor() {
		};

		private String jarname;

		private String groupId;

		private String artifactId;

		private String version;

		private String description;

		public String getJarname() {
			return jarname;
		}

		public void setJarname(String jarname) {
			this.jarname = jarname;
		}

		public String getArtifactId() {
			return artifactId;
		}

		public void setArtifactId(String artifactId) {
			this.artifactId = artifactId;
		}

		public String getVersion() {
			return version;
		}

		public void setVersion(String version) {
			this.version = version;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getGroupId() {
			return groupId;
		}

		public void setGroupId(String groupId) {
			this.groupId = groupId;
		}

		@Override
		public String toString() {
			return "JarDescriptor [jarname=" + jarname + ", groupId=" + groupId
					+ ", artifactId=" + artifactId + ", version=" + version
					+ ", description=" + description + "]";
		}

	}
}
