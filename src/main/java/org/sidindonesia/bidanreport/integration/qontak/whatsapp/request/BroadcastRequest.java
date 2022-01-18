package org.sidindonesia.bidanreport.integration.qontak.whatsapp.request;

import lombok.Data;

@Data
public class BroadcastRequest {
	private String name;
	private String message_template_id;
	private String contact_list_id;
	private String channel_integration_id;
	private Parameters parameters;
}
