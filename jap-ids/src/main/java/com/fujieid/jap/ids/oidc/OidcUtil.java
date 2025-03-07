/*
 * Copyright (c) 2020-2040, 北京符节科技有限公司 (support@fujieid.com & https://www.fujieid.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fujieid.jap.ids.oidc;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.fujieid.jap.ids.JapIds;
import com.fujieid.jap.ids.config.IdsConfig;
import com.fujieid.jap.ids.model.IdsConsts;
import com.fujieid.jap.ids.model.OidcDiscoveryDto;
import com.fujieid.jap.ids.model.enums.ClientSecretAuthMethod;
import com.fujieid.jap.ids.model.enums.GrantType;
import com.fujieid.jap.ids.model.enums.ResponseType;
import com.fujieid.jap.ids.provider.IdsScopeProvider;
import com.fujieid.jap.ids.util.EndpointUtil;
import com.fujieid.jap.ids.util.JwtUtil;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.ReservedClaimNames;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yadong.zhang (yadong.zhang0415(a)gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 */
public class OidcUtil {

    public static OidcDiscoveryDto getOidcDiscoveryInfo(HttpServletRequest request) {

        IdsConfig config = JapIds.getIdsConfig();
        List<String> scopes = IdsScopeProvider.getScopeCodes();

        String issuer = EndpointUtil.getIssuer(request);

        Map<String, Object> model = new HashMap<>();
        model.put("issuer", issuer);
        model.put("authorization_endpoint", EndpointUtil.getAuthorizeUrl(request));
        model.put("token_endpoint", EndpointUtil.getTokenUrl(request));
        model.put("userinfo_endpoint", EndpointUtil.getUserinfoUrl(request));
        model.put("registration_endpoint", EndpointUtil.getRegistrationUrl(request));
        model.put("end_session_endpoint", EndpointUtil.getEndSessionUrl(request));
        model.put("check_session_iframe", EndpointUtil.getCheckSessionUrl(request));
        model.put("jwks_uri", EndpointUtil.getJwksUrl(request));
        model.put("grant_types_supported", GrantType.grantTypes());
        model.put("response_modes_supported", Arrays.asList(
            "fragment",
            "query"));
        model.put("response_types_supported", ResponseType.responseTypes());
        model.put("scopes_supported", scopes);

        List<ClientSecretAuthMethod> clientSecretAuthMethods = config.getClientSecretAuthMethods();
        if (ObjectUtil.isEmpty(clientSecretAuthMethods)) {
            clientSecretAuthMethods = Collections.singletonList(ClientSecretAuthMethod.ALL);
        }
        if (clientSecretAuthMethods.contains(ClientSecretAuthMethod.ALL)) {
            model.put("token_endpoint_auth_methods_supported", ClientSecretAuthMethod.getAllMethods());
        } else {
            model.put("token_endpoint_auth_methods_supported", clientSecretAuthMethods
                .stream()
                .map(ClientSecretAuthMethod::getMethod)
                .collect(Collectors.toList()));
        }

        model.put("request_object_signing_alg_values_supported", Arrays.asList(
            "none",
            "RS256",
            "ES256"
            )
        );
        model.put("userinfo_signing_alg_values_supported", Arrays.asList(
            "RS256",
            "ES256"
            )
        );
        model.put("request_parameter_supported", true);
        model.put("request_uri_parameter_supported", true);
        model.put("require_request_uri_registration", false);
        model.put("claims_parameter_supported", true);
        model.put("id_token_signing_alg_values_supported", Arrays.asList(
            "RS256",
            "ES256"
            )
        );
        model.put("subject_types_supported", Collections.singletonList("public"));
        model.put("claims_supported", Arrays.asList(
            ReservedClaimNames.ISSUER,
            ReservedClaimNames.SUBJECT,
            ReservedClaimNames.AUDIENCE,
            ReservedClaimNames.EXPIRATION_TIME,
            ReservedClaimNames.ISSUED_AT,
            IdsConsts.NONCE,
            IdsConsts.AUTH_TIME,
            IdsConsts.USERNAME
        ));
        model.put("code_challenge_methods_supported", Arrays.asList(
            "PLAIN",
            "S256"
        ));
        return BeanUtil.mapToBean(model, OidcDiscoveryDto.class, false, null);
    }

    public static String getJwksPublicKey(String identity) {
        String jwksJson = JapIds.getContext().getIdentityService().getJwksJson(identity);
        JsonWebKeySet jsonWebKeySet = JwtUtil.IdsVerificationKeyResolver.createJsonWebKeySet(jwksJson);
        return jsonWebKeySet.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
    }
}
