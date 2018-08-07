/*
 * Copyright (c) Istituto Nazionale di Fisica Nucleare (INFN). 2016-2018
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
angular.module('dashboardApp').factory("Utils", Utils);

Utils.$inject = [];

function Utils() {
	
	var service = {
			s4: s4,
			uuid: uuid,
			isMe: isMe,
			isAdmin: isAdmin,
			isUser: isUser,
			getLoggedUser: getLoggedUser,
			isRegistrationEnabled: isRegistrationEnabled,
			isOidcEnabled: isOidcEnabled,
			isSamlEnabled: isSamlEnabled,
			buildErrorOperationResult: buildErrorOperationResult,
			buildSuccessOperationResult: buildSuccessOperationResult,
			buildErrorResult: buildErrorResult,
			userIsVoAdmin: userIsVoAdmin
		};

	return service;
	
	function s4() {
		return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
	}
	
	function uuid() {
		return this.s4() + this.s4() + '-' + this.s4() + '-' + this.s4() + '-' + this.s4() + '-' + this.s4()
			+ this.s4() + this.s4();
	}
	
	function isMe(id) {
		
		return (id == getUserInfo().sub);
	}

	function userIsVoAdmin(user){
		if (user.authorities) {
        if (user.authorities.indexOf('ROLE_ADMIN') > -1) {
          return true;
        }
      }
      return false;
	}
	
	function isAdmin() {
		
		return (getUserAuthorities().indexOf("ROLE_ADMIN") != -1);
	}
	
	function isUser() {
		
		return (getUserAuthorities().indexOf("ROLE_USER") != -1);
	}
	
	function getLoggedUser() {
		
		return { info: getUserInfo(), auth: getUserAuthorities(), isAdmin: isAdmin() };
	}

	function isRegistrationEnabled() {

		return getRegistrationEnabled();
	}
	
	function isOidcEnabled() {

		return getOidcEnabled();
	}
	
	function isSamlEnabled() {

		return getSamlEnabled();
	}

	function buildErrorResult(errorString) {

		return { 
			type: "error",
			text: errorString
		}
	}
	
	function buildErrorOperationResult(error) {

		return { 
			type: "error",
			text: error.data.error_description || error.data.detail || error.data.message
		}
	}

	function buildSuccessOperationResult(message) {

		return {
			type: "success",
			text: message
		}
	}
}