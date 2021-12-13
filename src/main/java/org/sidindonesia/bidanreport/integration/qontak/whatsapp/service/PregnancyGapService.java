package org.sidindonesia.bidanreport.integration.qontak.whatsapp.service;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.sidindonesia.bidanreport.config.property.LastIdProperties;
import org.sidindonesia.bidanreport.config.property.QRCodeProperties;
import org.sidindonesia.bidanreport.integration.qontak.config.property.QontakProperties;
import org.sidindonesia.bidanreport.integration.qontak.repository.AutomatedMessageStatsRepository;
import org.sidindonesia.bidanreport.integration.qontak.web.response.FileUploadResponse;
import org.sidindonesia.bidanreport.integration.qontak.whatsapp.request.BroadcastRequest;
import org.sidindonesia.bidanreport.integration.qontak.whatsapp.request.BroadcastRequest.ParametersWithHeader;
import org.sidindonesia.bidanreport.integration.qontak.whatsapp.service.util.BroadcastMessageService;
import org.sidindonesia.bidanreport.repository.MotherEditRepository;
import org.sidindonesia.bidanreport.repository.MotherIdentityRepository;
import org.sidindonesia.bidanreport.repository.projection.PregnancyGapProjection;
import org.sidindonesia.bidanreport.service.LastIdService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Transactional
@Service
public class PregnancyGapService {
	private static final String QR_CODE_GAP_CARE_PNG = "QR_Code-gap_care.png";
	private final QontakProperties qontakProperties;
	private final MotherIdentityRepository motherIdentityRepository;
	private final MotherEditRepository motherEditRepository;
	private final BroadcastMessageService broadcastMessageService;
	private final LastIdProperties lastIdProperties;
	private final LastIdService lastIdService;
	private final AutomatedMessageStatsRepository automatedMessageStatsRepository;
	private final WebClient webClient;
	private final Gson gson;
	private final QRCodeWriter qrCodeWriter;
	private final QRCodeProperties qrCodeProperties;
	private final Path qrCodeFilePath;
	private final FileSystemResource qrCodeFileSystemResource;

	public PregnancyGapService(QontakProperties qontakProperties, MotherIdentityRepository motherIdentityRepository,
		MotherEditRepository motherEditRepository, BroadcastMessageService broadcastMessageService,
		LastIdProperties lastIdProperties, LastIdService lastIdService,
		AutomatedMessageStatsRepository automatedMessageStatsRepository, WebClient webClient, Gson gson,
		QRCodeWriter qrCodeWriter, QRCodeProperties qrCodeProperties) {
		super();
		this.qontakProperties = qontakProperties;
		this.motherIdentityRepository = motherIdentityRepository;
		this.motherEditRepository = motherEditRepository;
		this.broadcastMessageService = broadcastMessageService;
		this.lastIdProperties = lastIdProperties;
		this.lastIdService = lastIdService;
		this.automatedMessageStatsRepository = automatedMessageStatsRepository;
		this.webClient = webClient;
		this.gson = gson;
		this.qrCodeWriter = qrCodeWriter;
		this.qrCodeProperties = qrCodeProperties;
		this.qrCodeFilePath = FileSystems.getDefault()
			.getPath(qrCodeProperties.getDirectoryPath() + QR_CODE_GAP_CARE_PNG);
		this.qrCodeFileSystemResource = new FileSystemResource(qrCodeFilePath);
	}

	@Scheduled(fixedRateString = "${scheduling.pregnancy-gap.fixed-rate-in-ms}", initialDelayString = "${scheduling.pregnancy-gap.initial-delay-in-ms}")
	public void sendPregnancyGapMessageToEnrolledMothers() {
		log.debug("Executing scheduled \"Inform Pregnancy Gap via WhatsApp\"...");
		log.debug("Send pregnancy gap message to all mothers according to their latest ANC visit");
		processRowsFromMotherIdentity();
		processRowsFromMotherEdit();

		lastIdService.syncANCVisitPregnancyGapLastId();
	}

	private void processRowsFromMotherIdentity() {
		List<PregnancyGapProjection> allPregnantWomenToBeInformedOfGapInTheirPregnancy = motherIdentityRepository
			.findAllPregnantWomenToBeInformedOfHerGapOnPregnancy(lastIdProperties.getAncVisitPregnancyGapLastId());

		broadcastPregnancyGapMessageTo(allPregnantWomenToBeInformedOfGapInTheirPregnancy);
	}

	private void processRowsFromMotherEdit() {
		List<PregnancyGapProjection> allPregnantWomenToBeInformedOfGapInTheirPregnancy = motherEditRepository
			.findAllPregnantWomenToBeInformedOfHerGapOnPregnancy(lastIdProperties.getAncVisitPregnancyGapLastId());

		broadcastPregnancyGapMessageTo(allPregnantWomenToBeInformedOfGapInTheirPregnancy);
	}

	private void broadcastPregnancyGapMessageTo(
		List<PregnancyGapProjection> allPregnantWomenToBeInformedOfGapInTheirPregnancy) {
		if (!allPregnantWomenToBeInformedOfGapInTheirPregnancy.isEmpty()) {
			AtomicLong pregnantGapSuccessCount = new AtomicLong();
			allPregnantWomenToBeInformedOfGapInTheirPregnancy.parallelStream()
				.forEach(broadcastPregnancyGapMessageViaWhatsApp(pregnantGapSuccessCount,
					qontakProperties.getWhatsApp().getPregnancyGapMessageTemplateId()));
			log.info("\"Inform Pregnancy Gap via WhatsApp\" for enrolled pregnant women completed.");
			log.info(
				"{} out of {} enrolled pregnant women have been informed of the gap in their pregnancy via WhatsApp successfully.",
				pregnantGapSuccessCount, allPregnantWomenToBeInformedOfGapInTheirPregnancy.size());

			automatedMessageStatsRepository.upsert(qontakProperties.getWhatsApp().getPregnancyGapMessageTemplateId(),
				"pregnancy_gap", pregnantGapSuccessCount.get(),
				allPregnantWomenToBeInformedOfGapInTheirPregnancy.size() - pregnantGapSuccessCount.get());
		}
	}

	private Consumer<PregnancyGapProjection> broadcastPregnancyGapMessageViaWhatsApp(AtomicLong successCount,
		String messageTemplateId) {
		return motherIdentity -> {
			BroadcastRequest requestBody = createPregnancyGapMessageRequestBody(motherIdentity, messageTemplateId);
			broadcastMessageService.sendBroadcastRequestToQontakAPI(successCount, motherIdentity, requestBody);
		};
	}

	private BroadcastRequest createPregnancyGapMessageRequestBody(PregnancyGapProjection motherIdentity,
		String messageTemplateId) {
		BroadcastRequest requestBody = broadcastMessageService.createBroadcastRequestBody(motherIdentity,
			messageTemplateId);

		setParametersForPregnancyGapMessage(motherIdentity, requestBody);
		return requestBody;
	}

	private void setParametersForPregnancyGapMessage(PregnancyGapProjection motherIdentity,
		BroadcastRequest requestBody) {
		String csv = motherIdentity.getPregnancyGapCommaSeparatedValues();
		List<String> values = Stream.of(csv.split(",")).map(String::trim).collect(toList());

		ParametersWithHeader parameters = new ParametersWithHeader();

		try {
			// TODO replace values.toString() to GapCare object
			BitMatrix bitMatrix = qrCodeWriter.encode(values.toString(), BarcodeFormat.QR_CODE,
				qrCodeProperties.getWidth(), qrCodeProperties.getHeight());
			MatrixToImageWriter.writeToPath(bitMatrix, "PNG", qrCodeFilePath);

			Mono<FileUploadResponse> response = webClient.post().uri(qontakProperties.getApiPathUploadFile())
				.body(BodyInserters.fromMultipartData("file", qrCodeFileSystemResource))
				.header("Authorization", "Bearer " + qontakProperties.getAccessToken()).retrieve()
				.bodyToMono(FileUploadResponse.class).onErrorResume(WebClientResponseException.class,
					ex -> ex.getRawStatusCode() == 422 || ex.getRawStatusCode() == 401
						? Mono.just(gson.fromJson(ex.getResponseBodyAsString(), FileUploadResponse.class))
						: Mono.error(ex));

			FileUploadResponse responseBody = response.block();
			if (responseBody != null) {
				if ("success".equals(responseBody.getStatus())) {
					parameters.getHeader().addHeaderParam("url", responseBody.getData().getUrl());
					parameters.getHeader().addHeaderParam("filename", QR_CODE_GAP_CARE_PNG);
				} else {
					log.error("Upload QR Code PNG file failed for: {}, at phone number: {}, with error details: {}",
						motherIdentity.getFullName(), motherIdentity.getMobilePhoneNumber(), responseBody.getError());
				}
			} else {
				log.error("Upload QR Code PNG file failed with no content for: {}, at phone number: {}",
					motherIdentity.getFullName(), motherIdentity.getMobilePhoneNumber());
			}
		} catch (WriterException | IOException e) {
			log.warn(Arrays.toString(e.getStackTrace()));
		}

		parameters.addBodyWithValues("1", "full_name", motherIdentity.getFullName());
		parameters.addBodyWithValues("2", "anc_date", values.get(0));
		parameters.addBodyWithValues("3", "gestational_age", values.get(1));
		parameters.addBodyWithValues("4", "height_in_cm", values.get(2));
		parameters.addBodyWithValues("5", "weight_in_kg", values.get(3));
		parameters.addBodyWithValues("6", "muac_in_cm", values.get(4));
		parameters.addBodyWithValues("7", "systolic_bp", values.get(5));
		parameters.addBodyWithValues("8", "diastolic_bp", values.get(6));
		parameters.addBodyWithValues("9", "uterine_f_height", values.get(7));
		parameters.addBodyWithValues("10", "fetal_presentati", values.get(8));
		parameters.addBodyWithValues("11", "fetal_heart_rate", values.get(9));
		parameters.addBodyWithValues("12", "tetanus_t_imm_st", values.get(10));
		parameters.addBodyWithValues("13", "given_tt_injecti", values.get(11));
		parameters.addBodyWithValues("14", "given_ifa_tablet", values.get(12));
		parameters.addBodyWithValues("15", "has_proteinuria", values.get(13));
		parameters.addBodyWithValues("16", "hb_level_result", values.get(14));
		parameters.addBodyWithValues("17", "glucose_140_mgdl", values.get(15));
		parameters.addBodyWithValues("18", "has_thalasemia", values.get(16));
		parameters.addBodyWithValues("19", "has_syphilis", values.get(17));
		parameters.addBodyWithValues("20", "has_hbsag", values.get(18));
		parameters.addBodyWithValues("21", "has_hiv", values.get(19));
		requestBody.setParameters(parameters);
	}
}