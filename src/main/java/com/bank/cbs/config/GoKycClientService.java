package com.bank.cbs.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bank.cbs.dto.response.GatewayApiResponse;
import com.bank.cbs.dto.response.GoKycVerifyResponse;
import com.bank.cbs.exception.BadRequestException;
import com.bank.cbs.exception.ResourceNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GoKycClientService {
    // ── Type token for the generic wrapper ─────────────────────────────────
    private static final ParameterizedTypeReference<GatewayApiResponse<GoKycVerifyResponse>>
            RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

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
            GatewayApiResponse<GoKycVerifyResponse> gatewayResponse = restClient.post()
                    .uri("/api/integration/kyc")
                    .body(new ExternalVerifyGatewayRequest(
                            "external_verify",
                            new ExternalVerifyData(idType, idNumber, bankId)
                    ))
                    .retrieve()
                    .body(RESPONSE_TYPE);   // ← unwraps { success, data: GoKycVerifyResponse }
 
            if (gatewayResponse == null) {
                throw new BadRequestException("Go_KYC gateway returned an empty response.");
            }
            if (!gatewayResponse.success() || gatewayResponse.data() == null) {
                String reason = gatewayResponse.error() != null
                        ? gatewayResponse.error()
                        : gatewayResponse.message();
                log.warn("Go_KYC verification failed: {}", reason);
                throw new BadRequestException("Go_KYC verification failed: " + reason);
            }
 
            return gatewayResponse.data();
 
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException(
                        "No KYC record found for idType=" + idType + ", idNumber=" + idNumber);
            }
            log.error("Go_KYC client error [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new BadRequestException("Go_KYC service returned an error: " + ex.getMessage());
 
        } catch (RestClientException ex) {
            log.error("Go_KYC service unavailable: {}", ex.getMessage());
            throw new BadRequestException("Go_KYC service is currently unavailable.");
        }
    }

    // ── Request wrapper records ──────────────────────────────────────────────────
    
    // Outer gateway envelope — {@code action} tells the gateway which route to proxy.
    private record ExternalVerifyGatewayRequest(String action, ExternalVerifyData data) {}

    // Inner payload forwarded to Go-KYC POST /api/v1/kyc/external-verify. Field names must be snake_case to match Go's JSON tags.
    private record ExternalVerifyData(String id_type, String id_number, String bank_id) {}
}
