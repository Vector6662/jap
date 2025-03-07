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
package com.fujieid.jap.oauth2;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.fujieid.jap.core.context.JapAuthentication;
import com.fujieid.jap.core.exception.JapOauth2Exception;
import com.fujieid.jap.oauth2.pkce.PkceCodeChallengeMethod;
import com.xkcoding.http.HttpUtil;
import com.xkcoding.json.JsonUtil;
import com.xkcoding.json.util.Kv;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth Strategy Util
 *
 * @author yadong.zhang (yadong.zhang0415(a)gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 */
public class Oauth2Util {

    private Oauth2Util() {
    }

    /**
     * create code_verifier for pkce mode only.
     * <p>
     * high-entropy cryptographic random STRING using the unreserved characters [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
     * from <a href="https://tools.ietf.org/html/rfc3986#section-2.3" target="_blank">Section 2.3 of [RFC3986]</a>, with a minimum length of 43 characters and a maximum length of 128 characters.
     *
     * @return String
     * @see <a href="https://docs.fujieid.com/college/protocol/oauth-2.0/oauth-2.0-pkce" target="_blank">https://docs.fujieid.com/college/protocol/oauth-2.0/oauth-2.0-pkce</a>
     */
    public static String generateCodeVerifier() {
        return Base64.encode(RandomUtil.randomString(50), "UTF-8");
    }

    /**
     * Suitable for OAuth 2.0 pkce enhancement mode.
     *
     * @param codeChallengeMethod s256 / plain
     * @param codeVerifier        Generated by the client
     * @return code challenge
     * @see <a href="https://tools.ietf.org/html/rfc7636#section-4.2" target="_blank">https://tools.ietf.org/html/rfc7636#section-4.2</a>
     * @see <a href="https://docs.fujieid.com/college/protocol/oauth-2.0/oauth-2.0-pkce" target="_blank">https://docs.fujieid.com/college/protocol/oauth-2.0/oauth-2.0-pkce</a>
     */
    public static String generateCodeChallenge(PkceCodeChallengeMethod codeChallengeMethod, String codeVerifier) {
        if (PkceCodeChallengeMethod.S256 == codeChallengeMethod) {
            // https://tools.ietf.org/html/rfc7636#section-4.2
            // code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
            return Base64.encodeUrlSafe(SecureUtil.sha256().digest(codeVerifier));
        } else {
            return codeVerifier;
        }
    }

    public static void checkOauthResponse(Kv responseKv, String errorMsg) {
        if (null == responseKv || responseKv.isEmpty()) {
            throw new JapOauth2Exception(errorMsg);
        }
        if (responseKv.containsKey("error") && ObjectUtil.isNotEmpty(responseKv.get("error"))) {
            throw new JapOauth2Exception(Optional.ofNullable(errorMsg).orElse("") +
                responseKv.get("error_description") + " " + responseKv.toString());
        }
    }

    public static void checkOauthCallbackRequest(String requestErrorParam, String requestErrorDescParam, String bizErrorMsg) {
        if (StrUtil.isNotEmpty(requestErrorParam)) {
            throw new JapOauth2Exception(Optional.ofNullable(bizErrorMsg).orElse("") + requestErrorDescParam);
        }
    }

    public static void checkState(String state, String clientId, boolean verifyState) {
        if (!verifyState) {
            return;
        }
        if (StrUtil.isEmpty(state) || StrUtil.isEmpty(clientId)) {
            throw new JapOauth2Exception("Illegal state.");

        }
        Serializable cacheState = JapAuthentication.getContext().getCache().get(Oauth2Const.STATE_CACHE_KEY.concat(clientId));
        if (null == cacheState || !cacheState.equals(state)) {
            throw new JapOauth2Exception("Illegal state.");
        }

    }

    /**
     * Check the validity of oauthconfig.
     * <p>
     * 1. For {@code tokenUrl}, this configuration is indispensable for any mode
     * 2. When responsetype = code:
     * - {@code authorizationUrl} and {@code userinfoUrl} cannot be null
     * - {@code clientId} cannot be null
     * - {@code clientSecret} cannot be null when PKCE is not enabled
     * 3. When responsetype = token:
     * - {@code authorizationUrl} and {@code userinfoUrl} cannot be null
     * - {@code clientId} cannot be null
     * - {@code clientSecret} cannot be null
     * 4. When GrantType = password:
     * - {@code username} and {@code password} cannot be null
     *
     * @param oAuthConfig oauth config
     */
    public static void checkOauthConfig(OAuthConfig oAuthConfig) {
        if (StrUtil.isEmpty(oAuthConfig.getTokenUrl())) {
            throw new JapOauth2Exception("Oauth2Strategy requires a tokenUrl");
        }
        // For authorization code mode and implicit authorization mode
        // refer to: https://tools.ietf.org/html/rfc6749#section-4.1
        // refer to: https://tools.ietf.org/html/rfc6749#section-4.2
        if (oAuthConfig.getResponseType() == Oauth2ResponseType.code ||
            oAuthConfig.getResponseType() == Oauth2ResponseType.token) {

            if (oAuthConfig.getResponseType() == Oauth2ResponseType.code) {
                if (oAuthConfig.getGrantType() != Oauth2GrantType.authorization_code) {
                    throw new JapOauth2Exception("Invalid grantType `" + oAuthConfig.getGrantType() + "`. " +
                        "When using authorization code mode, grantType must be `authorization_code`");
                }

                if (!oAuthConfig.isEnablePkce() && StrUtil.isEmpty(oAuthConfig.getClientSecret())) {
                    throw new JapOauth2Exception("Oauth2Strategy requires a clientSecret when PKCE is not enabled.");
                }
            } else {
                if (StrUtil.isEmpty(oAuthConfig.getClientSecret())) {
                    throw new JapOauth2Exception("Oauth2Strategy requires a clientSecret");
                }

            }
            if (StrUtil.isEmpty(oAuthConfig.getClientId())) {
                throw new JapOauth2Exception("Oauth2Strategy requires a clientId");
            }

            if (StrUtil.isEmpty(oAuthConfig.getAuthorizationUrl())) {
                throw new JapOauth2Exception("Oauth2Strategy requires a authorizationUrl");
            }

            if (StrUtil.isEmpty(oAuthConfig.getUserinfoUrl())) {
                throw new JapOauth2Exception("Oauth2Strategy requires a userinfoUrl");
            }
        }
        // For password mode
        // refer to: https://tools.ietf.org/html/rfc6749#section-4.3
        else {
            if (oAuthConfig.getGrantType() != Oauth2GrantType.password && oAuthConfig.getGrantType() != Oauth2GrantType.client_credentials) {
                throw new JapOauth2Exception("When the response type is none in the oauth2 strategy, a grant type other " +
                    "than the authorization code must be used: " + oAuthConfig.getGrantType());
            }
            if (oAuthConfig.getGrantType() == Oauth2GrantType.password) {
                if (!StrUtil.isAllNotEmpty(oAuthConfig.getUsername(), oAuthConfig.getPassword())) {
                    throw new JapOauth2Exception("Oauth2Strategy requires username and password in password certificate grant");
                }
            }
        }
    }

    /**
     * Whether it is the callback request after the authorization of the oauth platform is completed,
     * the judgment basis is as follows:
     * - When {@code response_type} is {@code code}, the {@code code} in the request parameter is empty
     * - When {@code response_type} is {@code token}, the {@code access_token} in the request parameter is empty
     *
     * @param request     callback request
     * @param oAuthConfig OAuthConfig
     * @return When true is returned, the current HTTP request is a callback request
     */
    public static boolean isCallback(HttpServletRequest request, OAuthConfig oAuthConfig) {
        if (oAuthConfig.getResponseType() == Oauth2ResponseType.code) {
            String code = request.getParameter("code");
            return !StrUtil.isEmpty(code);
        } else if (oAuthConfig.getResponseType() == Oauth2ResponseType.token) {
            String accessToken = request.getParameter("access_token");
            return !StrUtil.isEmpty(accessToken);
        }
        return false;
    }


    /**
     * Different third-party platforms may use different request methods,
     * and some third-party platforms have limited request methods, such as post and get.
     * <p>
     * In the {@code Oauth2Util#request(Oauth2EndpointMethodType, String, Map)},
     * Use the appropriate request method to obtain data by judging the {@code Oauth2EndpointMethodType}
     *
     * @param endpointMethodType Oauth2EndpointMethodType
     * @param url                request Url
     * @param params             Request parameters
     * @return Kv
     */
    public static Kv request(Oauth2EndpointMethodType endpointMethodType, String url, Map<String, String> params) {

        String res = null;
        if (null == endpointMethodType || Oauth2EndpointMethodType.GET == endpointMethodType) {
            res = HttpUtil.get(url, params, false);
        } else {
            res = HttpUtil.post(url, params, false);
        }
        return JsonUtil.parseKv(res);
    }
}
