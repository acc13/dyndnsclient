package io.yetanotherwhatever;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DynDNSClient {

	private static final Logger logger = LogManager.getLogger(DynDNSClient.class);
	
	private static String m_testip = null;
	
	public static void main(String[] args) throws InterruptedException 
	{
		//handle args
		String domainNamesArg = null;
		int frequencyInMins = 30;	//default
		
		
		for (int i = 0; i < args.length; i++)
		{
			if (args[i].equals("-d") && i < args.length)
			{
				domainNamesArg = args[i+1];
			}
			
			if (args[i].equals("-f") && i < args.length)
			{
				try
				{
					frequencyInMins = Integer.parseInt(args[i+1]);
				}
				catch (NumberFormatException e)
				{
					logger.error(e);
					usage();
				}
			}
			
			//for testing, so not printed by usage()
			if (args[i].equals("-i") && i < args.length)
			{
				m_testip = args[i+1];
			}
		}
		
		if(null == domainNamesArg)
		{
			usage();
		}
		
		String[] domainNames = domainNamesArg.split(";");
		
		
		String lastIP = null;	//cached old IP
		while (true)
		{
			String myIP = getWanIp();
			if (null == myIP)
			{
				logger.error("Failed to retrieve WAN IP.");
			}			
			else if (myIP.equals(lastIP))
			{
				logger.info("WAN IP unchanged.  Skipping update.");
			}
			else
			{
				if (lastIP != null)	//we have a  saved IP
				{
					logger.info("WAN IP changed from " + lastIP + " to " + myIP);
				}
				else
				{
					logger.info("WAN IP is " + myIP);
				}
				
				lastIP = myIP;	//cache
				
				IDNSManagementClient dnsClient = new AWSRoute53Client();
				for (String dn : domainNames)
				{
					dnsClient.synchronizeResourceRecord(dn, myIP);
				}
			}
			
			Thread.sleep(frequencyInMins * 1000 * 60);
		}
	}
	
	private static void usage()
	{
		System.out.println("Usage: " + DynDNSClient.class.getName() + " -d <DOMAIN NAMES> [-f <FREQUENCY>]");
		System.exit(1);
	}
	
	public static String getWanIp() 
	{
		if (m_testip != null)
			return m_testip;
		
		BufferedReader in = null;
        String retVal = null;

        try
        {
        	URL ipAdress = new URL("http://checkip.amazonaws.com");
            in = new BufferedReader(new InputStreamReader(ipAdress.openStream()));
            retVal = in.readLine();
        } 
        catch (IOException e) 
        {
            logger.error(e);
        }
        finally 
        {
        
            if (in != null) 
            {
                try 
                {
                    in.close();
                } 
                catch (IOException e) 
                {
                	logger.error(e);
                }
            }
        }
        
        return retVal;
    }

}
