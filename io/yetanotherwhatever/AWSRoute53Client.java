package io.yetanotherwhatever;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult;
import com.amazonaws.services.route53.model.DelegationSetNotReusableException;
import com.amazonaws.services.route53.model.HostedZone;
import com.amazonaws.services.route53.model.ListHostedZonesRequest;
import com.amazonaws.services.route53.model.ListHostedZonesResult;
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ListResourceRecordSetsResult;
import com.amazonaws.services.route53.model.NoSuchDelegationSetException;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.amazonaws.services.route53.model.InvalidInputException;

public class AWSRoute53Client implements IDNSManagementClient {

	private static final Logger logger = LogManager.getLogger(AWSRoute53Client.class);
	
	AmazonRoute53Client m_r53;
	
	public AWSRoute53Client ()
	{
		m_r53 = new AmazonRoute53Client();
	}
	
	public AWSRoute53Client (String accessKey, String secretKey)
	{
		m_r53 = new AmazonRoute53Client(new BasicAWSCredentials(accessKey, secretKey));
	}
	
	private List<ResourceRecordSet> findResourceRecordSets(String hzID)
	{
		ListResourceRecordSetsRequest lrrReq = new ListResourceRecordSetsRequest(hzID);
		ListResourceRecordSetsResult lrrRes = m_r53.listResourceRecordSets(lrrReq);
		List<ResourceRecordSet> rrsList = lrrRes.getResourceRecordSets();

		logger.debug("Resource record set found for hosted zone id " + hzID + ": " + rrsList);
		
		return rrsList;
	}
		
	private String getHostedZoneIdByName(String name)
	{
		
		ListHostedZonesRequest lhzReq= new ListHostedZonesRequest();
		ListHostedZonesResult hzRes;
		
		try
		{
			hzRes = m_r53.listHostedZones(lhzReq);
		}
		catch (InvalidInputException|NoSuchDelegationSetException|DelegationSetNotReusableException  e)
		{
			logger.error(e.toString());
			return null;
		}
		List<HostedZone> hzList = hzRes.getHostedZones();

		String hostedZoneId = null;
		for (HostedZone hz : hzList)
		{
			logger.trace(hz);
			if (hz.getName().equals(name))
			{
				hostedZoneId = hz.getId();
				logger.debug("Hosted zone " + name + " found with ID: " + hostedZoneId);
			}
		}

		if (null == hostedZoneId)
		{
			logger.warn("Hosted zone " + name + " not found.");
			//we don't create on failure, since hosted zones cost money
		}
		
		return hostedZoneId;
	}

	@Override
	public void synchronizeResourceRecord(String recordName, String ip)
	{
		if (null == recordName || null == ip)
		{
			return;
		}
		
		//add DNS root zone
		if (!recordName.endsWith("."))
		{
			recordName += ".";
		}
		
		//extract second level domain
		int indexTLD = recordName.lastIndexOf('.', recordName.length() - 2);	//ignore root level "dot" domain
		int indexSecondLD = recordName.lastIndexOf('.', indexTLD-1);
		String zoneName = (-1 == indexSecondLD)? recordName: recordName.substring(indexSecondLD+1);
		
		// Find the corresponding resource
		String hzID = getHostedZoneIdByName(zoneName);
		if(null == hzID)
		{
			//not found
			//no point in proceeding
			logger.error("zoneName " + zoneName + " not found");
			return;
		}
		
		List<ResourceRecordSet> rrsList = findResourceRecordSets(hzID);

		//search for A record containing value == ip
		ResourceRecordSet updateRRS = null;
		ResourceRecord updateRR = null;
		for (ResourceRecordSet rrs : rrsList)
		{
			if (rrs.getName().equals(recordName) && rrs.getType().equals("A"))
			{
				updateRRS = rrs;
				break;
			}
		}
		
		if (null == updateRRS)
		{
			logger.warn("Resource record set " + recordName + " not found.");
			
			updateRRS = new ResourceRecordSet();
			updateRRS.setName(recordName);
			updateRRS.setType("A");
			updateRRS.setTTL(300L);
			
			//add an empty resource record
			ResourceRecord rr = new ResourceRecord(); 
			List<ResourceRecord> rrList = Arrays.asList(rr);
			updateRRS.setResourceRecords(rrList);
		}

		List<ResourceRecord> rrList = updateRRS.getResourceRecords();
		
		//check values for our ip
		for (ResourceRecord rr : rrList)
		{
			if ( rr.getValue() != null && rr.getValue().equals(ip))
			{
				//we're in sync - no need to proceed
				logger.info("A record for " + recordName + " already set to " + ip + ".  No update necessary.");
				return;
			}
			logger.debug("Resource record value " + rr);
			updateRR = rr;	//we expect one record
		}
		
		if (null == updateRR)
		{
			logger.warn("Resource record set " + recordName + " contained no records??");
			updateRR = new ResourceRecord(ip);
		}
		else
		{
			logger.info("Wan IP change detected.  Updating from " + updateRR.getValue() + " to " + ip);
			updateRR.setValue(ip);
		}
		
		//update record
		changeResourceRecord(hzID, updateRRS);
	}
	
	private void changeResourceRecord(String zoneID, ResourceRecordSet rrs)
	{
		logger.debug("List for update " + rrs);
		
		Change c = new Change(ChangeAction.UPSERT, rrs);
		ChangeBatch cb = new ChangeBatch(Arrays.asList(c));
		ChangeResourceRecordSetsRequest crrsReq = new ChangeResourceRecordSetsRequest(zoneID, cb);
		ChangeResourceRecordSetsResult ccrsRes = m_r53.changeResourceRecordSets(crrsReq);
		
		logger.info(ccrsRes);
	}
}
