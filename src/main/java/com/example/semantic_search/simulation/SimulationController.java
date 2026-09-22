package com.example.semantic_search.simulation;

/**
 * Simülasyon denetleyicisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.controller.SimulationController} paketine taşınmıştır.
 */
@Deprecated
public class SimulationController extends com.example.semantic_search.controller.SimulationController {

    public SimulationController(com.example.semantic_search.service.OlaylarKafkaSimulationProducer producer,
                                com.example.semantic_search.repository.IndexingStateRepository indexingStateRepository) {
        super(producer, indexingStateRepository);
    }
}
