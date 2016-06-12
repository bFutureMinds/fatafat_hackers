package com.barclays.hackathon.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import opennlp.maxent.GISModel;
import opennlp.tools.doccat.DoccatModel;
import system.*;

import com.barclays.classifiers.MaxentGIS;
import com.barclays.classifiers.OpenNLPClassifier;
import com.barclays.classifiers.DT_ID3;

import com.google.common.base.Optional;
import com.ligadata.KamanjaBase.*;
import com.ligadata.kamanja.metadata.ModelDef;

public class HackathonModel extends ModelInstance {
	public HackathonModel(ModelInstanceFactory factory) {
		super(factory);
	}

	static final String SEPARATOR = ",";
	private OpenNLPClassifier openNLPModel = new OpenNLPClassifier();
	private MaxentGIS maxEntGISModel = new MaxentGIS();
	private DoccatModel openNLPMod;
	private GISModel maxEntMod;

	public ModelResultBase execute(TransactionContext txnCtxt,
			boolean outputDefault) {

		System.out.println("inside model");
		LifeMoments lf = null;

		socialmessage msg = (socialmessage) txnCtxt.getMessage();

		if ((!msg.user_name().equals( "Ashwath") && !msg.user_name().equals( "jane_doe") && !msg.user_name().equals("johnwrite")) || msg.user_name().equals(null)) {
			System.out.println("message not applicable!" + msg.user_name());
			return null;
		}
		try {
			initializeModels();

			lf = calculateAverageSentiment(msg.post());
		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
		System.out.println(lf);
		String prediction = "";
		if (lf == LifeMoments.HavingABaby) {
			String[] mapPartitionKey = { msg.user_name() };
			socialidtobankidmapcontainer mapping = (socialidtobankidmapcontainer) socialidtobankidmapcontainer
					.getRecentOrNew(mapPartitionKey);
			String[] masterPartitionKey = new String[1];
			if (mapping.getRDD().count() > 0) {
				masterPartitionKey[0] =  mapping.bank_customer_id() ;
			}else return null;
			customerarrtibutes master = (customerarrtibutes) customerarrtibutes
					.getRecentOrNew(masterPartitionKey);
			if (master.getRDD().count() ==0) {
				return null;
			}
			String fileData = "@relation pima_bank \n @attribute 'maritalstatus' { married, unmarried, divorced } \n @attribute 'age' numeric \n @attribute 'gender' { male, female } \n @attribute 'marriedsincemonths' numeric \n @attribute 'kidsstorevisits3months' numeric \n @attribute 'hospitalvisits1year' numeric \n @attribute 'events3months' numeric \n @attribute 'childsavingplan1month' numeric \n @attribute 'childinsuredmonths' numeric \n @attribute 'class' { no, yes } \n @data";
			fileData = fileData + "\n " + master.maritalstatus() + ","
					+ master.age() + "," + master.gender() + ","
					+ master.marriedsincemonths() + ","
					+ master.kidsstorevisits3months() + ","
					+ master.hospitalvisits1year() + ","
					+ master.events3months() + ","
					+ master.childsavingplan1month() + ","
					+ master.childinsured1month() + "," + master.actualresult();

			//System.out.println(fileData);
			DT_ID3 a = new DT_ID3();
			StringReader sr = new StringReader(fileData);
			BufferedReader reader = new BufferedReader(sr);
			prediction = (a.predict("/home/ec2-user/kamanja_package/Install/input/SampleApplications/data/bank_train.arff",	reader));
			System.out.println("prediction ==> "  + prediction);
			if (prediction.trim().equals("yes")) {
				//System.out.println("model positive");
				productholding ph = (productholding) productholding
						.getRecentOrNew(masterPartitionKey);
				if (ph.getRDD().count() > 0) {
					String[] productMappingKey = {"childbirth"};
					productmapping pm = (productmapping) productmapping.getRecentOrNew(productMappingKey);
					Result[] actualResult = {
							new Result("messageSource", msg.msg_source()),
							new Result("userName", msg.user_name()),
							new Result("Post", msg.post()),
							new Result("age", master.age()),
							new Result("maritalStatus", master.maritalstatus()),
							new Result("gender", master.gender()),
							new Result("predictionTime",
									System.currentTimeMillis() ),
							new Result("productName",pm.productname()),
							new Result("lifeMoment","HavingBaby")};

					return new MappedModelResults().withResults(actualResult);
				}
			}

		}
		System.out.println("model ran");
		return null;
	}

	public static String getCurrentTimeStamp() {
	    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy
	    Date now = new Date();
	    String strDate = sdfDate.format(now);
	    return strDate;
	}
	
	private void initializeModels() throws IOException {
		Path openNLPModelPath = Paths
				.get("/home/ec2-user/kamanja_package/Install//input/SampleApplications/data/Maxentropy_Serialized_Model");
		byte[] openNLPModelData = Files.readAllBytes(openNLPModelPath);
		openNLPMod = openNLPModel.deserialize(openNLPModelData);

		Path maxentGISModelPath = Paths
				.get("/home/ec2-user/kamanja_package/Install//input/SampleApplications/data/GISmaxEnt_MovieReview");
		byte[] maxentGISModelData = Files.readAllBytes(maxentGISModelPath);
		maxEntMod = maxEntGISModel.deserialize(maxentGISModelData);
	}

	private LifeMoments calculateAverageSentiment(String tweet)
			throws IOException {
		double modelSum = 0.0;
		modelSum += openNLPModel.getTweetSentimentScore(tweet, openNLPMod);
		modelSum += maxEntGISModel.getTweetSentimentScore(tweet, maxEntMod);

		return convertScoreToLifeMoment(modelSum / 2);
	}

	private static LifeMoments convertScoreToLifeMoment(double modelSum) {
		if (modelSum > 1.0 && modelSum <= 3.0)
			return LifeMoments.HavingABaby;
		else if (modelSum > 1.0 && modelSum <= 3.0)
			return LifeMoments.GettingMarried;
		else if (modelSum > 4.0 && modelSum <= 6.0)
			return LifeMoments.BuyingACar;
		else if (modelSum > 7.0 && modelSum <= 9.0)
			return LifeMoments.BuyingAHome;
		else if (modelSum > 10.0 && modelSum <= 12.0)
			return LifeMoments.ChangesAtWork;
		else if (modelSum > 13.0 && modelSum <= 15.0)
			return LifeMoments.Bereavement;
		else if (modelSum > 16.0 && modelSum <= 18.0)
			return LifeMoments.Separation;
		else if (modelSum > 19.0 && modelSum <= 21.0)
			return LifeMoments.Retirement;
		else
			return LifeMoments.NA;
	}

	public enum LifeMoments {
		HavingABaby, GettingMarried, BuyingACar, BuyingAHome, ChangesAtWork, Bereavement, Separation, Retirement, NA
	};

	public static class HackathonModelFactory extends ModelInstanceFactory {
		public HackathonModelFactory(ModelDef modelDef, NodeContext nodeContext) {
			super(modelDef, nodeContext);
		}

		public boolean isValidMessage(MessageContainerBase msg) {
			return (msg instanceof socialmessage);
		}

		public ModelInstance createModelInstance() {
			return new HackathonModel(this);
		}

		public String getModelName() {
			return "HackathonModel";
		}

		public String getVersion() {
			return "1.0.3";
		}

		public ModelResultBase createResultObject() {
			return new MappedModelResults();
		}
	}

}

