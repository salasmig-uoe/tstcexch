package uk.ac.ed.card.wso2;

import org.apache.synapse.MessageContext; 
import org.apache.synapse.SynapseLog;
import org.apache.synapse.SynapseException;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.Value;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;
import org.jaxen.JaxenException;

import java.lang.Object;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardExchangeAuthenticator extends AbstractMediator { 

    public boolean mediate(MessageContext synCtx) { 
        // Get client secret property and convert to byte array
        String key = "";
        try {
            Value expression = new Value(new SynapseXPath("wso2:vault-lookup('SIG_CARDEXCH_KEY')"));
            key = expression.evaluateValue(synCtx);
        } catch (JaxenException e) {
            _cardExchangeError("Error while accessing SIG_CARDEXCH_KEY in vault", synCtx);
        }
        
        if (key.isEmpty()) {
            _cardExchangeError("SIG_CARDEXCH_KEY not found as a secret", synCtx);
        }
        
        byte[] binaryKey = key.getBytes(StandardCharsets.UTF_8);

        Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
        org.apache.axis2.context.MessageContext axis2MessageCtx = axis2smc.getAxis2MessageContext();

        if (!JsonUtil.hasAJsonPayload(axis2MessageCtx)) {
            _cardExchangeError("Missing JSON payload in webhook request", synCtx);
        }
        
        byte[] payload = JsonUtil.jsonPayloadToByteArray(axis2MessageCtx); 
                
        String header = null;
        Object headers = axis2MessageCtx.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        if (headers != null && headers instanceof Map) {
            @SuppressWarnings("unchecked")
			Map<String, String> headersMap = (Map<String, String>) headers;
            header = headersMap.get("X-Signature");
        }
        if (header == null || header.trim().isEmpty()) {
            _cardExchangeError("Missing X-Signature on webhook request", synCtx);
        }
        
        Pattern pattern = Pattern.compile("(^|,)(t=(\\d+))(,|$)");
        Matcher matcher = pattern.matcher(header);
        if (!matcher.find()) {
            _cardExchangeError(String.format("Invalid X-Signature header timestamp (%s)", header), synCtx);
        }
        String timestamp = matcher.group(3);
        long requestTime = Long.parseLong(timestamp, 10);

        Instant instant = Instant.now();
        long currentTime = instant.getEpochSecond();

        if (currentTime < (requestTime - 3) || currentTime - requestTime > 3600) {
            _cardExchangeError(String.format("Invalid X-Signature header timestamp (%s)", header), synCtx);
        }
        
        pattern = Pattern.compile("(^|,)(v1=([^,]+))(,|$)");
        matcher = pattern.matcher(header);
        if (!matcher.find()) {
            _cardExchangeError(String.format("Invalid X-Signature header signature (%s)", header), synCtx);
        }
        String signature = matcher.group(3);
        
        byte[] timePart = timestamp.concat(".").getBytes(StandardCharsets.UTF_8);
        byte[] sign = new byte[timePart.length + payload.length];

        System.arraycopy(timePart, 0, sign, 0, timePart.length);
        System.arraycopy(payload, 0, sign, timePart.length, payload.length);
                    
        HMac hMac = new HMac(new SHA256Digest());
        hMac.init(new KeyParameter(binaryKey));
        hMac.update(sign, 0, sign.length);
        byte[] hmacOut = new byte[hMac.getMacSize()];
        hMac.doFinal(hmacOut, 0);
        String result = Hex.toHexString(hmacOut);

        if (!result.equals(signature)) {
            _cardExchangeError(String.format("X-Signature mismatch - ignoring message (%s)", header), synCtx);
        }

        return true;
    }
    
    private void _cardExchangeError(String message, MessageContext synCtx) {
        SynapseLog synLog = getLog(synCtx);
        synLog.auditFatal(String.format("CARDEXCHANGE: %s", message));
        synCtx.setProperty("CARDEXCHANGE_ERROR", message);
        throw new SynapseException(message);
    }
}
