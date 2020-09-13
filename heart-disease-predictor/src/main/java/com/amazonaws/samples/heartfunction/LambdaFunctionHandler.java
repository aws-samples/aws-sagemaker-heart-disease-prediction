package com.amazonaws.samples.heartfunction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntime;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeClientBuilder;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointRequest;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointResult;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

public class LambdaFunctionHandler implements RequestHandler<APIGatewayProxyRequestEvent, ApiGatewayResponse> {

	private static String FEATURES = "features";
	private static String INSTANCES = "instances";
	private static String SAGEMAKER_ENDPOINT = System.getenv("SAGEMAKER_ENDPOINT");
	private static String TOPIC_ARN = System.getenv("TOPIC_ARN");
	private static int HEART_DISEASE_PREDICTION = 1;
	private int prediction_label=0;
	private double score =0.0;

	// instantiate the clients
	private AmazonSageMakerRuntime sageMakerRuntime = AmazonSageMakerRuntimeClientBuilder.defaultClient();
	private AmazonSNS snsClient = AmazonSNSClientBuilder.defaultClient();

	public LambdaFunctionHandler() {
	}

	@Override
	public ApiGatewayResponse handleRequest(APIGatewayProxyRequestEvent  event, Context context) {
		
		
		if (event != null) {

			context.getLogger().log("incoming event data " + event.toString());
			JSONObject jsonObject = getEventData(event, context);
		
			// build the list of features (e.g. [57,1,0,140,192,0,1,148,0,0.4,1,0,1] )
			List<Object> featuresList = buildFeatures(jsonObject);

			context.getLogger().log("features: " + featuresList.toString());

			JSONObject request = buildRequest(featuresList);

			context.getLogger().log("SageMaker request data : " + request.toString());
			
			//get inference response from SageMaker
			JSONObject response = getInference(request, context);

			if (response != null) {
				context.getLogger().log("Inference response data : " + response.toString());
				JSONArray predictions =  response.getJSONArray("predictions");
				Iterator<Object> iter = predictions.iterator();
				while (iter.hasNext()) {
					JSONObject prediction = (JSONObject)iter.next();
					
					//get the prediction label - either 0 or 1
					prediction_label = (int)prediction.get("predicted_label");
					
					//get the prediction score
					score = ((Double) prediction.get("score")).doubleValue();
					context.getLogger()
							.log("recieved predition for heart disease with value of " + prediction_label);
					context.getLogger().log("prediction confidence level is " + score);

					// if the prediction is heart disease - send alert message via SNS.
					if (prediction_label == HEART_DISEASE_PREDICTION) {
						context.getLogger().log("Heart disases predicted with confidence score of " + score);
						String message = "We have prdicted that you may have a potential heart disease with confidence of "
								+ score;
						this.sendSNSMessage(message, context);
					}
				}

			}

		}
		return ApiGatewayResponse.builder().setStatusCode(200).setObjectBody("Prediction label is " + prediction_label + " with confidence of " + score)
				.setHeaders(Collections.singletonMap("X-Powered-By", "AWS API Gateway & Lambda Serverless"))
				.build();
	}

	private boolean sendSNSMessage(String message, Context context) {

		PublishRequest publishRequest = new PublishRequest(TOPIC_ARN, message);
		PublishResult publishResponse = snsClient.publish(publishRequest);

		// Print the MessageId of the message.

		context.getLogger().log("published message with following ID " + publishResponse.getMessageId());
		return true;
	}

	/**
	 * The purpose of this function is to invoke SageMaker end-point and get back
	 * inference (prediction)
	 * 
	 * @param request
	 * @param context
	 * @return - JSON Object representing SageMaker inference
	 */
	private JSONObject getInference(JSONObject request, Context context) {
		context.getLogger().log("Getting SageMaker inference to predict heart disease ");

		InvokeEndpointRequest invokeEndpointRequest = new InvokeEndpointRequest();
		invokeEndpointRequest.setContentType("application/json");

		try {
			invokeEndpointRequest.setBody(ByteBuffer.wrap(request.toString().getBytes("UTF-8")));
		} catch (java.io.UnsupportedEncodingException use) {

			context.getLogger().log("Unsuported sageMaker endpoint exception " + use.getMessage());

		}
		invokeEndpointRequest.setEndpointName(SAGEMAKER_ENDPOINT);

		InvokeEndpointResult result = sageMakerRuntime.invokeEndpoint(invokeEndpointRequest);

		String body = StandardCharsets.UTF_8.decode(result.getBody()).toString();

		JSONObject jsonResponse = new JSONObject(body);
			
		return jsonResponse;
		

	}
	/**
	 * The purpose of this method is to build the SageMaker request witht the given list of features.
	 * @param featuresList
	 * @return
	 */
	private JSONObject  buildRequest(List<Object> featuresList) {
		if (featuresList != null && !featuresList.isEmpty()) {
			JSONObject data = new JSONObject();
			JSONArray instances = new JSONArray();
			JSONObject features = new JSONObject();
			features.put(FEATURES, featuresList);
			data.put(INSTANCES, instances);
			instances.put(features);
			
			return data;
		}
		return null;
	}
	
	private JSONObject getEventData(APIGatewayProxyRequestEvent event, Context context) {
	
			context.getLogger().log("Event body is " + event.getBody());
		
			JSONObject jsonObject = new JSONObject(event.getBody());

			return jsonObject;
		
	}
	
	/**
	 * The purpose of this method is to build a list of features.
	 * @param event
	 * @return
	 */
	private List<Object> buildFeatures(JSONObject jsonObject) {
		List<Object> features = new Vector<Object>();
		if (jsonObject != null) {
			
			features.add(jsonObject.getString("age"));
			features.add(jsonObject.getInt("sex"));
			features.add(jsonObject.getInt("cp"));
			features.add(jsonObject.getInt("trestbps"));
			features.add(jsonObject.getInt("chol"));
			features.add(jsonObject.getInt("fbs"));
			features.add(jsonObject.getInt("restecg"));
			features.add(jsonObject.getInt("thalach"));
			features.add(jsonObject.get("exang"));
			features.add(jsonObject.get("oldpeak"));
			features.add(jsonObject.get("slope"));
			features.add(jsonObject.get("ca"));
			features.add(jsonObject.get("thal"));
		}
		return features;

	}
}