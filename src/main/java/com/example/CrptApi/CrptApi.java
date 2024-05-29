package com.example.CrptApi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class CrptApi {
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final BlockingQueue<Long> requestTimes;
	private final int requestLimit;
	private final long timeWindowMillis;

	public CrptApi(TimeUnit timeUnit, int requestLimit) {
		this.httpClient = HttpClient.newHttpClient();
		this.objectMapper = new ObjectMapper();
		this.requestLimit = requestLimit;
		this.timeWindowMillis = timeUnit.toMillis(1);
		this.requestTimes = new LinkedBlockingQueue<>(requestLimit);
	}

	public void createDocument(Object document, String signature) throws IOException, InterruptedException {
		synchronized (this) {
			while (true) {
				long currentTimeMillis = System.currentTimeMillis();
				if (requestTimes.size() < requestLimit) {
					requestTimes.add(currentTimeMillis);
					break;
				} else {
					long oldestRequestTime = requestTimes.peek();
					if (currentTimeMillis - oldestRequestTime > timeWindowMillis) {
						requestTimes.poll();
						requestTimes.add(currentTimeMillis);
						break;
					} else {
						long waitTime = timeWindowMillis - (currentTimeMillis - oldestRequestTime);
						wait(waitTime);
					}
				}
			}
		}

		String json = objectMapper.writeValueAsString(document);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
				.header("Content-Type", "application/json")
				.header("Signature", signature)
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		// Handle response (just printing here for simplicity)
		System.out.println("Response: " + response.body());
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

		// Example document object
		class Document {
			public String description;
			public String doc_id;
			public String doc_status;
			public String doc_type;
			public boolean importRequest;
			public String owner_inn;
			public String participant_inn;
			public String producer_inn;
			public String production_date;
			public String production_type;
			public Product[] products;
			public String reg_date;
			public String reg_number;

			class Product {
				public String certificate_document;
				public String certificate_document_date;
				public String certificate_document_number;
				public String owner_inn;
				public String producer_inn;
				public String production_date;
				public String tnved_code;
				public String uit_code;
				public String uitu_code;
			}
		}

		Document document = new Document();
		document.description = "ТЕстовое задание";
		document.doc_id = "12345";
		document.doc_status = "NEW";
		document.doc_type = "LP_INTRODUCE_GOODS";
		document.importRequest = true;
		document.owner_inn = "123456789";
		document.participant_inn = "0987654321";
		document.producer_inn = "1122334455";
		document.production_date = "2022-01-01";
		document.production_type = "SOME_TYPE";
		document.reg_date = "2022-01-01";
		document.reg_number = "REG12345";
		Document.Product product = document.new Product();
		product.certificate_document = "CERT_DOC";
		product.certificate_document_date = "2023-01-01";
		product.certificate_document_number = "CERT12345";
		product.owner_inn = "1234567890";
		product.producer_inn = "1122334455";
		product.production_date = "2023-01-01";
		product.tnved_code = "TNVED12345";
		product.uit_code = "UIT12345";
		product.uitu_code = "UITU12345";
		document.products = new Document.Product[]{product};

		// Пример подписи
		String signature = "example_signature";

		api.createDocument(document, signature);
	}
}