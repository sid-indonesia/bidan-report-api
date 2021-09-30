package org.sidindonesia.bidanreport.integration.qontak.response;

import java.util.List;

import lombok.Data;

@Data
public class QontakWhatsAppBroadcastResponse {
	private String status;
	private DataObj data;
	private ErrorObj error;

	@Data
	public static class DataObj {
		private String id;
		private String send_at;
		private String created_at;
	}

	@Data
	public static class ErrorObj {
		private Integer code;
		private List<Object> messages;
	}
}