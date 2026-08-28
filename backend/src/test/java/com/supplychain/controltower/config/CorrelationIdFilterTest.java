package com.supplychain.controltower.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void testExistingHeaderPreserved() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "test-correlation-123");

        doAnswer(invocation -> {
            assertEquals("test-correlation-123", MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertEquals("test-correlation-123", response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY), "MDC must be cleared after filter execution");
    }

    @Test
    void testMissingHeaderGeneratesNewUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertNotNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(responseHeader);
        assertFalse(responseHeader.isBlank());
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
    }

    @Test
    void testInvalidHeaderRegeneratesUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "invalid header with spaces & symbols !@#");

        filter.doFilter(request, response, filterChain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(responseHeader);
        assertNotEquals("invalid header with spaces & symbols !@#", responseHeader);
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
    }
}
