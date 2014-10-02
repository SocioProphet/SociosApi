package adaptors.twitter;

import helper.misc.OAuth;
import helper.misc.SociosConstants;
import helper.utilities.ContainerUtilities;
import helper.utilities.ExceptionsUtilities;
import helper.utilities.NetworkUtilities;
import helper.utilities.Utilities;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import objects.containers.MediaItemsContainer;
import objects.containers.ObjectIdContainer;
import objects.containers.PersonsContainer;
import objects.enums.SocialNetwork;
import objects.enums.SociosObject;
import objects.main.SociosException;
import objects.main.snException;
import org.json.JSONObject;
import com.sun.jersey.core.util.Base64;

public class TwitterCalls
{
	private static SocialNetwork sn = SocialNetwork.TWITTER;
	private static String baseUrl = "https://api.twitter.com/1.1/";
	private static String tokenUrl = "https://api.twitter.com/oauth2/token";
	private static String twitterUserAccessToken;
	private static String twitterUserAccessSecret;
	private static String twitterApplicationKey;
	private static String twitterApplicationSecret;
	private static String twitterBearerToken;
	private static String oauth_version = "1.0";
	private static String oauth_signature_method = "HMAC-SHA1";
	private static String host = "api.twitter.com";
	private static String userAgent = "Radical";
	private static String charset = "UTF-8";
	static
	{
		final InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("socios.properties");
		final Properties properties = new Properties();
		try
		{
			properties.load(inputStream);
			twitterUserAccessToken = properties.getProperty("twitterUserAccessToken");
			twitterUserAccessSecret = properties.getProperty("twitterUserAccessSecret");
			twitterApplicationKey = properties.getProperty("twitterApplicationKey");
			twitterApplicationSecret = properties.getProperty("twitterApplicationSecret");
			twitterBearerToken = properties.getProperty("twitterBearerToken");
		}
		catch (Exception exc)
		{
			System.out.println("Static initialization error" + sn + ": " + exc.getMessage());
		}
	}

	public static PersonsContainer getPerson(String identifier, String searchBy)
	{
		PersonsContainer result = new PersonsContainer();
		String url = "";
		if ("id".equals(searchBy))
		{
			url = baseUrl + "users/show.json?user_id=" + identifier;
		}
		else
		{
			url = baseUrl + "users/show.json?screen_name=" + identifier;
		}
		try
		{
			String response = getResponse(url);
			result = TwitterFetchers.fetchPerson(response, identifier);
		}
		catch (IOException exc)
		{
			return ExceptionsUtilities.getException(SociosObject.PERSON, sn, exc.getMessage(), identifier, 500);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.PERSON, sociosException, identifier);
		}
		return result;
	}

	public static PersonsContainer getPersons(String identifier, String searchBy)
	{
		PersonsContainer result = new PersonsContainer();
		String url = "";
		if ("id".equals(searchBy))
		{
			url = baseUrl + "users/lookup.json?user_id=" + identifier;
		}
		else
		{
			url = baseUrl + "users/lookup.json?screen_name=" + identifier;
		}
		try
		{
			String response = getResponse(url);
			result = TwitterFetchers.fetchPersons(response, identifier);
		}
		catch (IOException exc)
		{
			return ExceptionsUtilities.getException(SociosObject.PERSON, sn, exc.getMessage(), identifier, 500);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.PERSON, sociosException, identifier);
		}
		return result;
	}

	public static PersonsContainer getConnectedPersons(final String id)
	{
		final PersonsContainer result = new PersonsContainer();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		pool.submit(new Runnable()
		{
			@Override
			public void run()
			{
				PersonsContainer friends = getConnectedPersons(id, "friends");
				ContainerUtilities.merge(result, friends);
				return;
			}
		});
		pool.submit(new Runnable()
		{
			@Override
			public void run()
			{
				PersonsContainer followers = getConnectedPersons(id, "followers");
				ContainerUtilities.merge(result, followers);
				return;
			}
		});
		pool.shutdown();
		try
		{
			pool.awaitTermination(SociosConstants.timeOut, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		pool.shutdownNow();
		ContainerUtilities.cleanExceptions(result);
		return result;
	}

	private static PersonsContainer getConnectedPersons(String id, String type)
	{
		PersonsContainer result = new PersonsContainer();
		String requestUrl = baseUrl + type + "/list.json?id=" + id + "&count=200";
		try
		{
			String response = getResponse(requestUrl);
			result = TwitterFetchers.fetchFriends(response, id);
		}
		catch (IOException exc)
		{
			return ExceptionsUtilities.getException(SociosObject.PERSON, sn, exc.getMessage(), id, 500);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.PERSON, sociosException, id);
		}
		return result;
	}

	public static PersonsContainer getPersonForMediaItem(String id)
	{
		PersonsContainer result = new PersonsContainer();
		String url = baseUrl + "statuses/show.json?id=" + id;
		try
		{
			String response = getResponse(url);
			result = TwitterFetchers.fetchPersonForMediaItem(response, id);
		}
		catch (IOException exc)
		{
			return ExceptionsUtilities.getException(SociosObject.PERSON, sn, exc.getMessage(), id, 500);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.PERSON, sociosException, id);
		}
		return result;
	}

	public static PersonsContainer searchAuthPersons(String query)
	{
		PersonsContainer result = new PersonsContainer();
		String requestUrl = baseUrl + "users/search.json";
		Map<String, String> map = new HashMap<String, String>();
		map.put("q", OAuth.percentEncode(query));
		try
		{
			String response = getResponse(requestUrl, map);
			result = TwitterFetchers.fetchPersons(response, null);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.PERSON, sociosException, null);
		}
		catch (Exception exc)
		{
			return ExceptionsUtilities.getException(SociosObject.PERSON, sn, exc.getMessage(), null, 500);
		}
		return result;
	}

	public static MediaItemsContainer getMediaItem(String id)
	{
		MediaItemsContainer result = new MediaItemsContainer();
		String url = baseUrl + "statuses/show.json?id=" + id;
		try
		{
			String response = getResponse(url);
			result = TwitterFetchers.fetchMediaItem(response, id);
		}
		catch (IOException exc)
		{
			return ExceptionsUtilities.getException(SociosObject.MEDIAITEM, sn, exc.getMessage(), id, 500);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.MEDIAITEM, sociosException, id);
		}
		return result;
	}

	public static MediaItemsContainer getMediaItemsForUser(final String token, final String searchBy)
	{
		final MediaItemsContainer result = new MediaItemsContainer();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		pool.submit(new Runnable()
		{
			@Override
			public void run()
			{
				MediaItemsContainer statuses = getMediaItemsForUser(token, searchBy, "statuses/user_timeline.json");
				ContainerUtilities.merge(result, statuses);
				return;
			}
		});
		pool.submit(new Runnable()
		{
			@Override
			public void run()
			{
				MediaItemsContainer favorites = getMediaItemsForUser(token, searchBy, "favorites/list.json");
				ContainerUtilities.merge(result, favorites);
				return;
			}
		});
		pool.shutdown();
		try
		{
			pool.awaitTermination(SociosConstants.timeOut, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		pool.shutdownNow();
		ContainerUtilities.cleanExceptions(result);
		return result;
	}

	private static MediaItemsContainer getMediaItemsForUser(String identifier, String searchBy, String type)
	{
		MediaItemsContainer result = new MediaItemsContainer();
		String requestUrl = "";
		if ("id".equals(searchBy))
		{
			requestUrl = baseUrl + type + "?id=" + identifier;
		}
		else
		{
			requestUrl = baseUrl + type + "?screen_name=" + identifier;
		}
		try
		{
			String response = getResponse(requestUrl + "&count=200");
			result = TwitterFetchers.fetchMediaItems(response, identifier);
		}
		catch (IOException exc)
		{
			return ExceptionsUtilities.getException(SociosObject.MEDIAITEM, sn, exc.getMessage(), identifier, 500);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.MEDIAITEM, sociosException, identifier);
		}
		return result;
	}

	public static MediaItemsContainer getRetweets(String id)
	{
		MediaItemsContainer result = new MediaItemsContainer();
		String requestUrl = baseUrl + "statuses/retweets/" + id + ".json?count=100";
		try
		{
			String response = getResponse(requestUrl);
			result = TwitterFetchers.fetchStatuses(response, id);
		}
		catch (IOException exc)
		{
			return ExceptionsUtilities.getException(SociosObject.MEDIAITEM, sn, exc.getMessage(), id, 500);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.MEDIAITEM, sociosException, id);
		}
		return result;
	}

	public static MediaItemsContainer searchMediaItems(String query)
	{
		MediaItemsContainer result = new MediaItemsContainer();
		String requestUrl = baseUrl + "search/tweets.json?" + query + "&count=100";
		try
		{
			String response = getResponse(requestUrl);
			result = TwitterFetchers.fetchStatuses(response, null);
		}
		catch (IOException exc)
		{
			return ExceptionsUtilities.getException(SociosObject.MEDIAITEM, sn, exc.getMessage(), null, 500);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.MEDIAITEM, sociosException, null);
		}
		return result;
	}

	public static ObjectIdContainer postMessage(String status, String accessToken, String accessSecret)
	{
		ObjectIdContainer result = new ObjectIdContainer();
		HttpsURLConnection connection = null;
		try
		{
			String url = "https://api.twitter.com/1.1/statuses/update.json";
			String uuid_string = UUID.randomUUID().toString();
			String oauth_nonce = uuid_string.replaceAll("-", "");
			String oauth_timestamp = String.valueOf(System.currentTimeMillis() / 1000);
			Map<String, String> map = getMap(oauth_nonce, oauth_timestamp);
			map.put(OAuth.percentEncode("status"), OAuth.percentEncode(status));
			String baseString = "POST&" + OAuth.percentEncode(url) + "&" + OAuth.percentEncode(getChain(map));
			String signingKey = OAuth.percentEncode(twitterApplicationSecret) + "&" + OAuth.percentEncode(accessSecret);
			String signature = Utilities.computeSignature(baseString, signingKey);
			String headerTemplate = "OAuth oauth_consumer_key=\"%s\",oauth_nonce=\"%s\",oauth_signature_method=\"%s\",oauth_signature=\"%s\",oauth_timestamp=\"%s\",oauth_token=\"%s\",oauth_version=\"%s\"";
			String header = String.format(headerTemplate, OAuth.percentEncode(twitterApplicationKey), OAuth.percentEncode(oauth_nonce),
					OAuth.percentEncode(oauth_signature_method), OAuth.percentEncode(signature), OAuth.percentEncode(oauth_timestamp),
					OAuth.percentEncode(accessToken), OAuth.percentEncode(oauth_version));
			URL obj = new URL(url);
			connection = (HttpsURLConnection) obj.openConnection();
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Host", host);
			connection.setRequestProperty("User-Agent", userAgent);
			connection.setRequestProperty("Authorization", header);
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			status = OAuth.percentEncode(status);
			connection.setRequestProperty("Content-Length", String.valueOf(status.length() + 7));
			connection.setUseCaches(false);
			NetworkUtilities.writeRequest(connection, "status=" + status);
			String response = NetworkUtilities.readResponse(connection);
			result = TwitterFetchers.fetchMessageId(response);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.OBJECTID, sociosException, null);
		}
		catch (Exception exc)
		{
			return ExceptionsUtilities.getException(SociosObject.OBJECTID, sn, exc.getMessage(), null, 500);
		}
		return result;
	}

	public static String postMessageWithPhoto(String postText, String fileName, String fileData, String accessToken, String accessSecret)
	{
		byte[] bytes = Base64.decode(fileData);
		HttpsURLConnection connection = null;
		String result = "";
		try
		{
			String url = "https://api.twitter.com/1.1/statuses/update_with_media.json";
			String uuid_string = UUID.randomUUID().toString();
			String oauth_nonce = uuid_string.replaceAll("-", "");
			String oauth_timestamp = String.valueOf(System.currentTimeMillis() / 1000);
			Map<String, String> map = getMap(oauth_nonce, oauth_timestamp);
			String baseString = "POST&" + OAuth.percentEncode(url) + "&" + OAuth.percentEncode(getChain(map));
			String signingKey = OAuth.percentEncode(twitterApplicationSecret) + "&" + OAuth.percentEncode(accessSecret);
			String signature = Utilities.computeSignature(baseString, signingKey);
			String headerTemplate = "OAuth oauth_consumer_key=\"%s\",oauth_nonce=\"%s\",oauth_signature_method=\"%s\",oauth_signature=\"%s\",oauth_timestamp=\"%s\",oauth_token=\"%s\",oauth_version=\"%s\"";
			String header = String.format(headerTemplate, OAuth.percentEncode(twitterApplicationKey), OAuth.percentEncode(oauth_nonce),
					OAuth.percentEncode(oauth_signature_method), OAuth.percentEncode(signature), OAuth.percentEncode(oauth_timestamp),
					OAuth.percentEncode(accessToken), OAuth.percentEncode(oauth_version));
			String boundary = "===" + System.currentTimeMillis() + "===";
			String LINE_FEED = "\r\n";
			URL obj = new URL(url);
			connection = (HttpsURLConnection) obj.openConnection();
			connection.setUseCaches(false);
			connection.setDoOutput(true); //indicates POST method
			connection.setDoInput(true);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Host", host);
			connection.setRequestProperty("User-Agent", userAgent);
			connection.setRequestProperty("Authorization", header);
			connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			connection.setRequestProperty("Accept-Encoding", "gzip");
			OutputStream outputStream = connection.getOutputStream();
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);
			writer.append("--" + boundary).append(LINE_FEED);
			writer.append("Content-Disposition: form-data; name=\"status\"").append(LINE_FEED);
			writer.append("Content-Type: text/plain; charset=" + charset).append(LINE_FEED);
			writer.append(LINE_FEED);
			writer.append(postText).append(LINE_FEED);
			writer.flush();
			writer.append("--" + boundary).append(LINE_FEED);
			writer.append("Content-Disposition: form-data; name=\"media[]\"; filename=\"" + fileName + "\"").append(LINE_FEED);
			writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(fileName)).append(LINE_FEED);
			writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
			writer.append(LINE_FEED);
			writer.flush();
			outputStream.write(bytes, 0, bytes.length);
			outputStream.flush();
			writer.append(LINE_FEED);
			writer.flush();
			writer.append(LINE_FEED).flush();
			writer.append("--" + boundary + "--").append(LINE_FEED);
			writer.close();
			result = NetworkUtilities.readResponse(connection);
		}
		catch (snException exc)
		{
			SociosException sociosException = TwitterParsers.parseNativeException(exc.data);
			return ExceptionsUtilities.getNativeException(SociosObject.OBJECTID, sociosException, null);
		}
		catch (Exception exc)
		{
			return ExceptionsUtilities.getException(SociosObject.OBJECTID, sn, exc.getMessage(), null, 500);
		}
		return "SUCCESS : !!! " + result;
	}

	private static String getResponse(String endPointUrl) throws snException, IOException
	{
		HttpURLConnection connection = null;
		URL url;
		String result = null;
		url = new URL(endPointUrl);
		connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestProperty("Host", host);
		connection.setRequestProperty("User-Agent", userAgent);
		connection.setRequestProperty("Authorization", "Bearer " + twitterBearerToken);
		connection.setUseCaches(false);
		try
		{
			result = NetworkUtilities.readResponse(connection);
		}
		catch (IOException exc)
		{
			InputStream error = connection.getErrorStream();
			result = NetworkUtilities.getStringFromInputStream(error);
			snException dataCarrier = new snException();
			dataCarrier.data = result;
			throw dataCarrier;
		}
		return result;
	}

	private static String getResponse(String endPointUrl, Map<String, String> map) throws Exception
	{
		HttpsURLConnection conn = null;
		String url = endPointUrl + "?";
		if (!map.isEmpty())
		{
			int size = map.size();
			for (Map.Entry<String, String> entry : map.entrySet())
			{
				size--;
				String key = entry.getKey().toString();
				String value = entry.getValue().toString();
				url += key + "=" + value;
				if (size != 0)
				{
					url += "&";
				}
			}
		}
		long millis = System.currentTimeMillis() / 1000;
		String uuid_string = UUID.randomUUID().toString();
		String oauth_nonce = uuid_string.replaceAll("-", "");
		String oauth_timestamp = String.valueOf(millis);
		map.put(OAuth.percentEncode("oauth_consumer_key"), OAuth.percentEncode(twitterApplicationKey));
		map.put(OAuth.percentEncode("oauth_nonce"), OAuth.percentEncode(oauth_nonce));
		map.put(OAuth.percentEncode("oauth_signature_method"), OAuth.percentEncode(oauth_signature_method));
		map.put(OAuth.percentEncode("oauth_timestamp"), OAuth.percentEncode(oauth_timestamp));
		map.put(OAuth.percentEncode("oauth_token"), OAuth.percentEncode(twitterUserAccessToken));
		map.put(OAuth.percentEncode("oauth_version"), OAuth.percentEncode(oauth_version));
		String baseString = "GET&" + OAuth.percentEncode(endPointUrl) + "&" + OAuth.percentEncode(getChain(map));
		String signingKey = OAuth.percentEncode(twitterApplicationSecret) + "&" + OAuth.percentEncode(twitterUserAccessSecret);
		String signature = Utilities.computeSignature(baseString, signingKey);
		String headerTemplate = "OAuth oauth_consumer_key=\"%s\",oauth_nonce=\"%s\",oauth_signature_method=\"%s\",oauth_signature=\"%s\",oauth_timestamp=\"%s\",oauth_token=\"%s\",oauth_version=\"%s\"";
		String header = String.format(headerTemplate, OAuth.percentEncode(twitterApplicationKey), OAuth.percentEncode(oauth_nonce),
				OAuth.percentEncode(oauth_signature_method), OAuth.percentEncode(signature), OAuth.percentEncode(oauth_timestamp),
				OAuth.percentEncode(twitterUserAccessToken), OAuth.percentEncode(oauth_version));
		URL connUrl = new URL(url);
		conn = (HttpsURLConnection) connUrl.openConnection();
		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Host", host);
		conn.setRequestProperty("User-Agent", userAgent);
		conn.setRequestProperty("Authorization", header);
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charset);
		conn.setRequestProperty("Accept-Charset", charset);
		conn.setUseCaches(false);
		String result;
		try
		{
			result = NetworkUtilities.readResponse(conn);
		}
		catch (IOException exc)
		{
			InputStream error = conn.getErrorStream();
			result = NetworkUtilities.getStringFromInputStream(error);
			snException dataCarrier = new snException();
			dataCarrier.data = result;
			throw dataCarrier;
		}
		return result;
	}

	public static String getBearerToken()
	{
		HttpsURLConnection connection = null;
		String encodedCredentials = Utilities.encodeKeys(twitterApplicationKey, twitterApplicationSecret);
		try
		{
			URL url = new URL(tokenUrl);
			connection = (HttpsURLConnection) url.openConnection();
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Host", host);
			connection.setRequestProperty("User-Agent", userAgent);
			connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charset);
			connection.setRequestProperty("Content-Length", "29");
			connection.setUseCaches(false);
			NetworkUtilities.writeRequest(connection, "grant_type=client_credentials");
			String response = NetworkUtilities.readResponse(connection);
			JSONObject obj = new JSONObject(response);
			if (obj != null)
			{
				String tokenType = (String) obj.get("token_type");
				String token = (String) obj.get("access_token");
				String result = (("bearer".equals(tokenType)) && (token != null)) ? token : "";
				return result;
			}
		}
		catch (Exception exc)
		{
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
		}
		return null;
	}

	private static String getChain(Map<String, String> map)
	{
		Map<String, String> sortedMap = new TreeMap<String, String>(map);
		int size = sortedMap.size();
		String chain = "";
		for (Map.Entry<String, String> entry : sortedMap.entrySet())
		{
			size--;
			String key = entry.getKey().toString();
			String value = entry.getValue().toString();
			chain += key + "=" + value;
			if (size != 0)
			{
				chain += "&";
			}
		}
		return chain;
	}

	private static Map<String, String> getMap(String oauth_nonce, String oauth_timestamp)
	{
		Map<String, String> map = new HashMap<String, String>();
		map.put(OAuth.percentEncode("oauth_consumer_key"), OAuth.percentEncode(twitterApplicationKey));
		map.put(OAuth.percentEncode("oauth_nonce"), OAuth.percentEncode(oauth_nonce));
		map.put(OAuth.percentEncode("oauth_signature_method"), OAuth.percentEncode(oauth_signature_method));
		map.put(OAuth.percentEncode("oauth_timestamp"), OAuth.percentEncode(oauth_timestamp));
		map.put(OAuth.percentEncode("oauth_version"), OAuth.percentEncode(oauth_version));
		map.put(OAuth.percentEncode("oauth_token"), OAuth.percentEncode(twitterUserAccessToken));
		return map;
	}
}
