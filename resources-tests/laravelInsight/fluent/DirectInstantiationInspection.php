<?php

namespace {
    use Illuminate\Support\Fluent as BaseFluent;

    class FacadeFluent extends BaseFluent {
    }
}

namespace Illuminate\Support {
    class Fluent {
    }
}

namespace MyNamespace {
    use Illuminate\Support\Fluent as FluentDirect;

    // Not acceptable: Fluent should not be instantiated directly.
    $fluentInstantiatedDirectly = new <weak_warning descr="Fluent should not be instantiated directly.">FluentDirect</weak_warning>();

    class ChildFluent extends Fluent
    {
    }

    // Acceptable: Fluent is intantiated via ChildFluent.
    $fluentInstantiatedIndirectly = new ChildFluent();

    // Not acceptable: Fluent should not be instantiated, even from Facade.
    $fluentInstantiatedViaFacade = new <weak_warning descr="Fluent should not be instantiated directly.">\FacadeFluent</weak_warning>();
}
