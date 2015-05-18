package ai.vital.wordnet

import ai.vital.domain.Edge_hasWordnetPointer;
import ai.vital.domain.SynsetNode;
import ai.vital.lucene.model.LuceneSegmentType;
import ai.vital.service.lucene.model.LuceneVitalSegment
import ai.vital.vitalservice.VitalService;
import ai.vital.vitalservice.admin.VitalServiceAdmin;
import ai.vital.vitalservice.factory.VitalServiceFactory;
import ai.vital.vitalservice.model.App
import ai.vital.vitalservice.segment.VitalSegment;
import ai.vital.vitalsigns.model.GraphObject
import ai.vital.vitalsigns.block.BlockCompactStringSerializer;
import ai.vital.vitalsigns.block.BlockCompactStringSerializer.BlockIterator;
import ai.vital.vitalsigns.block.BlockCompactStringSerializer.VitalBlock;

class VitalWordnetImport {

	// import wordnet
	
	static void o(String m) {System.out.println(m);}
	
	static int BATCH_SIZE = 1000
	
	
	public static void main(String[] args) {
		
		if(args.length != 2) {
			o("usage: vitalwordnetimport <service_profile> <input_block>")
			o("       input block name must end with .vital or .vital.gz")
			return
		}
		
		String profile = args[0]
		println "Service profile: ${profile}"
		
		VitalServiceFactory.setServiceProfile(profile)
		
		File inputBlock = new File(args[1])
		println "Input block file: ${inputBlock.absolutePath}"
		
		println "Getting service instance..."
		VitalServiceAdmin service = VitalServiceFactory.getVitalServiceAdmin()
		
		println "Service instance: ${service}"
		
		BlockIterator iterator = null
		
		App defaultApp = new App(ID:'app')
		boolean appexists = false
		for(App app : service.listApps()) {
			if(app.ID == defaultApp.ID) {
				appexists = true
				break
			}
		}
		
		if(!appexists) {
			println "Default app does not exist - adding..."
			service.addApp(defaultApp)
		} else {
			println "Default app already exists"
		}
		
		VitalSegment existing = null
		for(VitalSegment segment : service.listSegments(defaultApp)) {
			if(segment.ID == 'wordnet') {
				existing = segment
				break
			}
		}
		
		if(existing != null) {
			println "Existing wordnet segment found - removing..."
			service.removeSegment(defaultApp, existing, true)
		}
		
		println "Creating new wordnet segment..."
		existing = new LuceneVitalSegment()
		existing.appID = defaultApp.ID
		existing.ID = 'wordnet'
		existing.readOnly = false
		existing.storeObjects = true
		existing.type = LuceneSegmentType.disk
		
		service.addSegment(defaultApp, existing, true)
		
		println "Inserting wordnet data - ${BATCH_SIZE} objects per batch"
		
		int nodes = 0
		int edges = 0
		int skipped = 0
		List<GraphObject> batch = new ArrayList<GraphObject>()
		
		int inserted = 0;
		
		try {
			
			for( iterator = BlockCompactStringSerializer.getBlocksIterator(inputBlock); iterator.hasNext(); ) {
				
				VitalBlock block = iterator.next()
				
				List<GraphObject> objects = [block.getMainObject()]
				objects.addAll(block.getDependentObjects())
				
				for(GraphObject g : objects) {
					
					if(g instanceof SynsetNode) {
						nodes++
					} else if(g instanceof Edge_hasWordnetPointer) {
						edges++
					} else {
						skipped++
						continue		
					}
					
					batch.add(g)
					
				}
				
				if(batch.size() >= BATCH_SIZE) {
					
					service.save(defaultApp, existing, batch, true)
					
					inserted += batch.size()
					
					println ("inserted: ${inserted}")
					
					batch.clear()
					
				}
				
			}
			
			if(batch.size() > 0) {
				
				service.save(defaultApp, existing, batch, true)
				
			}
			
			inserted += batch.size()
			
			println ("total objects inserted: ${inserted}")
			
		} finally {
			if(iterator != null) iterator.close()
			service.close()
		}
		
		println "DONE, nodes: ${nodes}, edges: ${edges}, skipped: ${skipped}"
		
	}

}