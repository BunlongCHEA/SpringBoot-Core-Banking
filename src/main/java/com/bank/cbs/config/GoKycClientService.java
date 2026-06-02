package com.bank.cbs.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bank.cbs.dto.response.GoKycVerifyResponse;
import com.bank.cbs.exception.BadRequestException;
import com.bank.cbs.exception.ResourceNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GoKycClientService {
    private final RestClient restClient;

    public GoKycClientService(@Qualifier("goKycRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Calls the Go_KYC external-verify endpoint.
     *
     * @param idType  document type (e.g. "NATIONAL_ID", "PASSPORT")
     * @param idNumber the document number
     * @param bankId  the requesting bank's ID in Go_KYC
     * @return the verified KYC record
     * @throws ResourceNotFoundException when no matching KYC record exists
     * @throws BadRequestException       on any Go_KYC client error
     */
    public GoKycVerifyResponse verifyCustomer(String idType, String idNumber, String bankId) {
        try {
            return restClient.post()
                    .uri("/api/v1/kyc/external-verify")
                    .body(new ExternalVerifyRequest(idType, idNumber, bankId))
                    .retrieve()
                    .body(GoKycVerifyResponse.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException(
                        "No KYC record found for idType=" + idType + ", idNumber=" + idNumber);
            }
            log.error("Go_KYC client error: {}", ex.getMessage());
            throw new BadRequestException("Go_KYC service returned an error: " + ex.getMessage());
        } catch (RestClientException ex) {
            log.error("Go_KYC service unavailable: {}", ex.getMessage());
            throw new BadRequestException("Go_KYC service is currently unavailable.");
        }
    }

    /** Internal request record for the Go_KYC endpoint. */
    private record ExternalVerifyRequest(
            String id_type,
            String id_number,
            String bank_id
    ) {}
}
