package com.amazonaws.samples.heartfunction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntime;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeClientBuilder;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointRequest;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointResult;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

public class LambdaFunctionHandler implements RequestHandler<HeartData, ApiGatewayResponse> {

	private static String FEATURES = "features";
	private static String INSTANCES = "instances";
	private static String SAGEMAKER_ENDPOINT = System.getenv("SAGEMAKER_ENDPOINT");
	private static String TOPIC_ARN = System.getenv("TOPIC_ARN");
	private static double HEART_DISEASE_PREDICTION = 1.0;
	private double prediction_label=0.0;
	private double score =0.0;

	// instantiate the clients
	private AmazonSageMakerRuntime sageMakerRuntime = AmazonSageMakerRuntimeClientBuilder.defaultClient();
	private AmazonSNS snsClient = AmazonSNSClientBuilder.defaultClient();

	public LambdaFunctionHandler() {
	}

	@Override
	public ApiGatewayResponse handleRequest(HeartData event, Context context) {
		
		
		if (event != null) {

			// build the list of features (e.g. [57,1,0,140,192,0,1,148,0,0.4,1,0,1] )
			List<Object> featuresList = buildFeatures(event);

			context.getLogger().log("features: " + featuresList.toString());

			JSONObject request = buildRequest(featuresList);

			context.getLogger().log("SageMaker request data : " + request.toJSONString());
			
			//get inference response from SageMaker
			JSONObject response = getInference(request, context);

			if (response != null) {
				context.getLogger().log("Inference response data : " + response.toJSONString());
				JSONArray predictions = (JSONArray) response.get("predictions");
				Iterator<JSONObject> iter = predictions.iterator();
				while (iter.hasNext()) {
					JSONObject prediction = iter.next();
					//get the prediciton label
					prediction_label = ((Double) prediction.get("predicted_label")).doubleValue();
					
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
			invokeEndpointRequest.setBody(ByteBuffer.wrap(request.toJSONString().getBytes("UTF-8")));
		} catch (java.io.UnsupportedEncodingException use) {

			context.getLogger().log("Unsuported sageMaker endpoint exception " + use.getMessage());

		}
		invokeEndpointRequest.setEndpointName(SAGEMAKER_ENDPOINT);

		InvokeEndpointResult result = sageMakerRuntime.invokeEndpoint(invokeEndpointRequest);

		String body = StandardCharsets.UTF_8.decode(result.getBody()).toString();
		JSONParser parser = new JSONParser();
		try {
			JSONObject jsonResponse = (JSONObject) parser.parse(body);
			return jsonResponse;
		} catch (ParseException pe) {
			context.getLogger().log("Parsing exception " + pe.getMessage());
		}
		context.getLogger().log("Unable to get inference from  SageMaker ");
		return null;

	}
	/**
	 * The purpose of this method is to build the SageMaker request witht the given list of features.
	 * @param featuresList
	 * @return
	 */
	private JSONObject buildRequest(List<Object> featuresList) {
		if (featuresList != null && !featuresList.isEmpty()) {
			JSONObject data = new JSONObject();
			JSONArray instances = new JSONArray();
			JSONObject features = new JSONObject();
			features.put(FEATURES, featuresList);
			data.put(INSTANCES, instances);
			instances.add(features);
			return data;
		}
		return null;
	}
	
	/**
	 * The purpose of this method is to build a list of features.
	 * @param event
	 * @return
	 */
	private List<Object> buildFeatures(HeartData event) {
		List<Object> features = new Vector<Object>();
		if (event != null) {
			features.add(event.getAge());
			features.add(event.getSex());
			features.add(event.getCp());
			features.add(event.getTrestbps());
			features.add(event.getChol());
			features.add(event.getFbs());
			features.add(event.getRestecg());
			features.add(event.getThalach());
			features.add(event.getExang());
			features.add(event.getOldpeak());
			features.add(event.getSlope());
			features.add(event.getCa());
			features.add(event.getThal());
		}
		return features;

	}
}