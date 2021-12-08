package org.sidindonesia.bidanreport.integration.qontak.whatsapp.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.sidindonesia.bidanreport.integration.qontak.config.property.QontakProperties;
import org.sidindonesia.bidanreport.integration.qontak.repository.AutomatedMessageStatsRepository;
import org.sidindonesia.bidanreport.integration.qontak.whatsapp.request.BroadcastRequest;
import org.sidindonesia.bidanreport.integration.qontak.whatsapp.request.BroadcastRequest.Parameters;
import org.sidindonesia.bidanreport.integration.qontak.whatsapp.service.util.BroadcastMessageService;
import org.sidindonesia.bidanreport.repository.MotherEditRepository;
import org.sidindonesia.bidanreport.repository.MotherIdentityRepository;
import org.sidindonesia.bidanreport.repository.projection.AncVisitReminderProjection;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
@Service
public class VisitReminderService {
	private final QontakProperties qontakProperties;
	private final MotherIdentityRepository motherIdentityRepository;
	private final MotherEditRepository motherEditRepository;
	private final BroadcastMessageService broadcastMessageService;
	private final AutomatedMessageStatsRepository automatedMessageStatsRepository;

	@Scheduled(cron = "${scheduling.visit-reminder.cron}", zone = "${scheduling.visit-reminder.zone}")
	public void sendVisitRemindersToEnrolledMothers() {
		log.debug("Executing scheduled \"Send ANC Visit Reminder via WhatsApp\"...");
		log.debug("Send ANC visit reminder to all mothers with -"
			+ qontakProperties.getWhatsApp().getVisitReminderIntervalInDays() + " day(s) for the next ANC visit date");
		processRowsFromMotherIdentity();
		processRowsFromMotherEdit();
	}

	private void processRowsFromMotherIdentity() {
		List<AncVisitReminderProjection> allPregnantWomenToBeRemindedForTheNextANCVisit = motherIdentityRepository
			.findAllPregnantWomenToBeRemindedForTheNextANCVisit(qontakProperties.getWhatsApp().getVisitIntervalInDays(),
				qontakProperties.getWhatsApp().getVisitReminderIntervalInDays());

		broadcastANCVisitReminderMessageTo(allPregnantWomenToBeRemindedForTheNextANCVisit);
	}

	private void processRowsFromMotherEdit() {
		List<AncVisitReminderProjection> allPregnantWomenToBeRemindedForTheNextANCVisit = motherEditRepository
			.findAllPregnantWomenToBeRemindedForTheNextANCVisit(qontakProperties.getWhatsApp().getVisitIntervalInDays(),
				qontakProperties.getWhatsApp().getVisitReminderIntervalInDays());

		broadcastANCVisitReminderMessageTo(allPregnantWomenToBeRemindedForTheNextANCVisit);
	}

	private void broadcastANCVisitReminderMessageTo(
		List<AncVisitReminderProjection> allPregnantWomenToBeRemindedForTheNextANCVisit) {
		if (!allPregnantWomenToBeRemindedForTheNextANCVisit.isEmpty()) {
			AtomicLong visitReminderSuccessCount = new AtomicLong();
			allPregnantWomenToBeRemindedForTheNextANCVisit.parallelStream()
				.forEach(broadcastANCVisitReminderMessageViaWhatsApp(visitReminderSuccessCount,
					qontakProperties.getWhatsApp().getVisitReminderMessageTemplateId()));
			log.info("\"Send ANC Visit Reminder via WhatsApp\" for enrolled pregnant women completed.");
			log.info(
				"{} out of {} enrolled pregnant women have been reminded of the next ANC visit via WhatsApp successfully.",
				visitReminderSuccessCount, allPregnantWomenToBeRemindedForTheNextANCVisit.size());

			automatedMessageStatsRepository.upsert(qontakProperties.getWhatsApp().getVisitReminderMessageTemplateId(),
				"anc_visit_reminder", visitReminderSuccessCount.get(),
				allPregnantWomenToBeRemindedForTheNextANCVisit.size() - visitReminderSuccessCount.get());
		}
	}

	private Consumer<AncVisitReminderProjection> broadcastANCVisitReminderMessageViaWhatsApp(AtomicLong successCount,
		String messageTemplateId) {
		return ancVisitReminderProjection -> {
			BroadcastRequest requestBody = createANCVisitReminderMessageRequestBody(ancVisitReminderProjection,
				messageTemplateId);
			broadcastMessageService.sendBroadcastRequestToQontakAPI(successCount, ancVisitReminderProjection,
				requestBody);
		};
	}

	private BroadcastRequest createANCVisitReminderMessageRequestBody(
		AncVisitReminderProjection ancVisitReminderProjection, String messageTemplateId) {
		BroadcastRequest requestBody = broadcastMessageService.createBroadcastRequestBody(ancVisitReminderProjection,
			messageTemplateId);

		setParametersForANCVisitReminderMessage(ancVisitReminderProjection, requestBody);
		return requestBody;
	}

	private void setParametersForANCVisitReminderMessage(AncVisitReminderProjection ancVisitReminderProjection,
		BroadcastRequest requestBody) {
		Parameters parameters = new Parameters();
		parameters.addBodyWithValues("1", "full_name", ancVisitReminderProjection.getFullName());
		parameters.addBodyWithValues("2", "visit_number",
			String.valueOf(ancVisitReminderProjection.getLatestAncVisitNumber() + 1));
		requestBody.setParameters(parameters);
	}
}