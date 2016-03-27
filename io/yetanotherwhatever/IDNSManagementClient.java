package io.yetanotherwhatever;

public interface IDNSManagementClient {
	public void synchronizeResourceRecord(String resourceRecordName, String ip);
}
