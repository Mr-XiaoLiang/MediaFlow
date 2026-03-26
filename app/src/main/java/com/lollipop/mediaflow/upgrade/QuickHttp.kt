package com.lollipop.mediaflow.upgrade

import com.lollipop.mediaflow.tools.QuickResult
import com.lollipop.mediaflow.tools.mapTo
import com.lollipop.mediaflow.tools.mapValue
import okhttp3.Call
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

fun QuickResult<Call>.quickExecute(): QuickResult<Response> {
    return mapValue { it.execute() }
}

fun QuickResult<Response>.stringBody(): QuickResult<String> {
    return mapTo { response ->
        if (response.code == 200) {
            QuickResult.Success(response.body.string())
        } else {
            QuickResult.Failure(HttpException(response.code, response.message))
        }
    }
}

fun QuickResult<String>.jsonObjectResult(): QuickResult<JSONObject> {
    return mapValue { response ->
        JSONObject(response)
    }
}

fun QuickResult<String>.jsonArrayResult(): QuickResult<JSONArray> {
    return mapValue { response ->
        JSONArray(response)
    }
}


class HttpException(val code: Int, val msg: String) : RuntimeException("code: $code, msg: $msg")
