package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void doFilterInternal_generatesAFreshCorrelationIdWhenNoHeaderIsPresent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(null);

        filter.doFilter(request, response, chain);

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER_NAME), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).isNotBlank();
        verify(chain).doFilter(request, response);
        // MDC is cleared once the filter chain returns - proves no leakage across requests.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilterInternal_reusesAnIncomingCorrelationIdHeaderInsteadOfGeneratingANewOne() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("incoming-id");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(CorrelationIdFilter.HEADER_NAME, "incoming-id");
    }

    @Test
    void doFilterInternal_populatesMdcDuringTheChainSoDownstreamCodeCanReadIt() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("mid-request-id");
        FilterChain chain = (req, res) -> assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("mid-request-id");

        filter.doFilter(request, response, chain);
    }
}
