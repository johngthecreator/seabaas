package com.seabaas.factories;

import com.seabaas.controllers.CollectionController;
import com.seabaas.services.CollectionService;
import com.seabaas.repositories.CollectionRepository;
import org.jdbi.v3.core.Jdbi;

public class CollectionFactory {
    public static CollectionController create(Jdbi jdbi){
        var repository = new CollectionRepository(jdbi);
        var service = new CollectionService(repository);
        return new CollectionController(service);
    }
}
