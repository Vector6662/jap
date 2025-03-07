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
package com.fujieid.jap.ids.endpoint;

import com.fujieid.jap.ids.JapIds;
import com.fujieid.jap.ids.exception.IdsException;
import com.fujieid.jap.ids.model.IdsResponse;
import com.fujieid.jap.ids.model.UserInfo;
import com.fujieid.jap.ids.pipeline.IdsPipeline;
import com.fujieid.jap.ids.util.EndpointUtil;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Logout Endpoint
 *
 * @author yadong.zhang (yadong.zhang0415(a)gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 */
public class LogoutEndpoint extends AbstractEndpoint {

    public IdsResponse<String, String> logout(HttpServletRequest request, ServletResponse response) {
        IdsPipeline<UserInfo> logoutPipeline = JapIds.getContext().getLogoutPipeline();
        logoutPipeline = this.getUserInfoIdsPipeline(logoutPipeline);
        if (!logoutPipeline.preHandle(request, response)) {
            throw new IdsException("IdsLogoutPipeline<UserInfo>.preHandle returns false, the process is blocked.");
        }
        JapIds.removeUserInfo(request);
        request.getSession().invalidate();

        logoutPipeline.afterHandle(request, response);
        return new IdsResponse<String, String>()
            .data(EndpointUtil.getLogoutRedirectUrl(request));
    }
}
