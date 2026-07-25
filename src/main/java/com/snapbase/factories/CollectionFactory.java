package com.snapbase.factories;

import com.snapbase.controllers.CollectionController;
import com.snapbase.services.CollectionService;
import com.snapbase.repositories.CollectionRepository;
import org.jdbi.v3.core.Jdbi;

public class CollectionFactory {
    public static CollectionController create(Jdbi jdbi){
        var repository = new CollectionRepository(jdbi);
        var service = new CollectionService(repository);
        return new CollectionController(service);
    }
}
