package org.mockserver.client.netty.codec;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.FullHttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.*;
import org.mockserver.model.Cookie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;
import static org.mockserver.model.Parameter.param;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;

/**
 * @author jamesdbloom
 */
public class MockServerRequestEncoderTest {

    private MockServerRequestEncoder mockServerRequestEncoder;
    private List<Object> output;
    private OutboundHttpRequest httpRequest;

    @Before
    public void setupFixture() {
        mockServerRequestEncoder = new MockServerRequestEncoder();
        output = new ArrayList<Object>();
        httpRequest = outboundRequest("localhost", 80, "", request());
    }

    @Test
    public void shouldEncodeMethod() {
        // given
        httpRequest.withMethod("OPTIONS");

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpMethod method = ((FullHttpRequest) output.get(0)).getMethod();
        assertThat(method, is(HttpMethod.OPTIONS));
    }

    @Test
    public void shouldEncodeQueryParameters() {
        // given
        httpRequest
                .withPath("/uri")
                .withQueryStringParameters(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                );

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).getUri();
        assertThat(uri, is("/uri?" +
                "queryStringParameterNameOne=queryStringParameterValueOne_One&" +
                "queryStringParameterNameOne=queryStringParameterValueOne_Two&" +
                "queryStringParameterNameTwo=queryStringParameterValueTwo_One"));
    }

    @Test
    public void shouldEscapeQueryParameters() {
        // given
        httpRequest
                .withPath("/uri")
                .withQueryStringParameters(
                        param("parameter name with spaces", "a value with double \"quotes\" and spaces"),
                        param("another parameter", "a value with single \'quotes\' and spaces")
                );

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).getUri();
        assertThat(uri, is("/uri?" +
                "parameter%20name%20with%20spaces=a%20value%20with%20double%20%22quotes%22%20and%20spaces&" +
                "another%20parameter=a%20value%20with%20single%20%27quotes%27%20and%20spaces"));
    }

    @Test
    public void shouldEncodePath() {
        // given
        httpRequest.withPath("/other_path");

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).getUri();
        assertThat(uri, is("/other_path"));
    }

    @Test
    public void shouldEncodeHeaders() {
        // given
        httpRequest.withHeaders(
                new Header("headerName1", "headerValue1"),
                new Header("headerName2", "headerValue2_1", "headerValue2_2")
        );

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("headerName1"), containsInAnyOrder("headerValue1"));
        assertThat(headers.getAll("headerName2"), containsInAnyOrder("headerValue2_1", "headerValue2_2"));
    }

    @Test
    public void shouldEncodeDefaultNonSecureHostHeader() {
        // given
        httpRequest = outboundRequest("localhost", 80, "", request());

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost"));
    }

    @Test
    public void shouldEncodeNonDefaultNonSecureHostHeader() {
        // given
        httpRequest = outboundRequest("localhost", 666, "", request());

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost:666"));
    }

    @Test
    public void shouldEncodeDefaultSecureHostHeader() {
        // given
        httpRequest = outboundRequest("localhost", 443, "", request().withSecure(true));

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost"));
    }

    @Test
    public void shouldEncodeNonDefaultSecureHostHeader() {
        // given
        httpRequest = outboundRequest("localhost", 999, "", request().withSecure(true));

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost:999"));
    }

    @Test
    public void shouldEncodeNoHeaders() {
        // given
        httpRequest.withHeaders((Header[]) null);

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.names(), containsInAnyOrder(
                "Host",
                "Accept-Encoding",
                "Content-Length",
                "Connection"
        ));
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost"));
        assertThat(headers.getAll("Accept-Encoding"), containsInAnyOrder("gzip,deflate"));
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("0"));
        assertThat(headers.getAll("Connection"), containsInAnyOrder("keep-alive"));
    }

    @Test
    public void shouldEncodeCookies() {
        // given
        httpRequest.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Cookie"), is(Arrays.asList("cookieName1=cookieValue1; cookieName2=cookieValue2")));
    }

    @Test
    public void shouldEncodeNoCookies() {
        // given
        httpRequest.withCookies((Cookie[]) null);

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Cookie"), emptyIterable());
    }

    @Test
    public void shouldEncodeStringBody() {
        // given
        httpRequest.withBody("somebody");

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_8), is("somebody"));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), is(MediaType.create("text", "plain").toString()));
    }

    @Test
    public void shouldEncodeBinaryBody() {
        // given
        httpRequest.withBody(binary("somebody".getBytes()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().array(), is("somebody".getBytes()));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), nullValue());
    }

    @Test
    public void shouldEncodeNullBody() {
        // given
        httpRequest.withBody((String) null);

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_8), is(""));
    }

    @Test
    public void shouldDecodeBodyWithContentTypeAndNoCharset() {
        // given
        httpRequest.withBody("A normal string with ASCII characters");
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is("A normal string with ASCII characters"));
    }

    @Test
    public void shouldDecodeBodyWithNoContentType() {
        // given
        httpRequest.withBody("A normal string with ASCII characters");

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is("A normal string with ASCII characters"));
    }

    @Test
    public void shouldTransmitUnencodableCharacters() {
        // given
        httpRequest.withBody("Euro sign: \u20AC", ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET);
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is(new String("Euro sign: \u20AC".getBytes(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldUseDefaultCharsetIfCharsetNotSupported() {
        // given
        httpRequest.withBody("A normal string with ASCII characters");
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=invalid-charset"));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is("A normal string with ASCII characters"));
    }

    @Test
    public void shouldDecodeBodyWithUTF8ContentType() {
        // given
        httpRequest.withBody("avro işarəsi: \u20AC", Charsets.UTF_8);
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_8), is("avro işarəsi: \u20AC"));
    }

    @Test
    public void shouldDecodeBodyWithUTF16ContentType() {
        // given
        httpRequest.withBody("我说中国话", Charsets.UTF_16);
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_16), is("我说中国话"));
    }

    @Test
    public void shouldEncodeStringBodyWithCharset() {
        // given
        httpRequest.withBody("我说中国话", Charsets.UTF_16);

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_16), is("我说中国话"));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), is(MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString()));
    }

    @Test
    public void shouldEncodeJsonBodyWithCharset() {
        // given
        httpRequest.withBody(json("{ \"some_field\": \"我说中国话\" }", Charsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_16), is("{ \"some_field\": \"我说中国话\" }"));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), is(MediaType.JSON_UTF_8.withCharset(Charsets.UTF_16).toString()));
    }

    @Test
    public void shouldPreferStringBodyCharacterSet() {
        // given
        httpRequest.withBody("avro işarəsi: \u20AC", Charsets.UTF_16);
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").withCharset(Charsets.US_ASCII).toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_16), is("avro işarəsi: \u20AC"));
    }
}
