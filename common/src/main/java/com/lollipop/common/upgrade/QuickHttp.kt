package com.lollipop.common.upgrade

import com.lollipop.common.tools.TaskResult
import com.lollipop.common.tools.mapTo
import com.lollipop.common.tools.mapValue
import okhttp3.Call
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

fun TaskResult<Call>.quickExecute(): TaskResult<Response> {
    return mapValue { it.execute() }
}

fun TaskResult<Response>.stringBody(): TaskResult<String> {
    return mapTo { response ->
        if (response.code == 200) {
            TaskResult.Success(response.body.string())
        } else {
            TaskResult.Failure(HttpException(response.code, response.message))
        }
    }
}

fun TaskResult<String>.jsonObjectResult(): TaskResult<JSONObject> {
    return mapValue { response ->
        JSONObject(response)
    }
}

fun TaskResult<String>.jsonArrayResult(): TaskResult<JSONArray> {
    return mapValue { response ->
        JSONArray(response)
    }
}


class HttpException(val code: Int, val msg: String) : RuntimeException("code: $code, msg: $msg")
