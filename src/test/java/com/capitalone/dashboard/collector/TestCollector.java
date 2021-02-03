package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.repository.LibraryReferenceRepository;
import com.capitalone.dashboard.repository.WhiteSourceCollectorRepository;
import com.capitalone.dashboard.repository.WhiteSourceComponentRepository;
import com.capitalone.dashboard.settings.WhiteSourceSettings;
import org.springframework.scheduling.TaskScheduler;

public class TestCollector extends WhiteSourceCollectorTask{
    public TestCollector(TaskScheduler taskScheduler, WhiteSourceCollectorRepository whiteSourceCollectorRepository, WhiteSourceComponentRepository whiteSourceComponentRepository, WhiteSourceClient whiteSourceClient, WhiteSourceSettings whiteSourceSettings, LibraryReferenceRepository libraryReferenceRepository, AsyncService dataRefreshService) {
        super(taskScheduler, whiteSourceCollectorRepository, whiteSourceComponentRepository, whiteSourceClient, new WhiteSourceSettings(), libraryReferenceRepository, dataRefreshService);
    }

    @Override
    public String getCron() {
        return "* * * * * *";
    }
}
