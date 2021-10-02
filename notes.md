ctrl, ctrl + up/dow => multiple selection !!!
dTw | dtw: delete until: <- | ->
diw : delete the current word (inside) or y (yank)
ctrl+a : increment the first number
ctrl+x : decrement the first number

buffer
:badd <name>
:ls list buffer
:b1 access by buffer number
:bn | :bp

macro
q<name>- yyp ctr+a q
@<name> to run macro

gd: got definition
gu<$>: uncapitalize (guu - all lines) - gU3W: Capitalize
gf: open the file (go to file ?)
gv; got back to tex selected

ctrl + alt + enter -> try to complete
alt + enter        -> suggestion
alt up/down        -> (next method)
ctrl + shift up/down  -> move current selection
ctrl + alt + l    -> format

ctrl + n         -> got to (classes)
ctrl + shift + n -> got to (files)
shift + shift    -> go to (all) 
shift + escp -> close current view (window) !!! 

ctrl + alt + v -> extract variable
ctrl + alt + p -> extract parameter
ctrl + alt + c -> extract constant
ctrl + alt + f -> extract field

ctrl + alt + n -> inline variable
ctrl + alt + m -> extract methods

ctrl + b       -> follow the method
ctrl + alt + b -> implementation (from the super class)
ctrl + f12     -> go to upper definition super class

To free "Alt + F1" -> gsettings set org.gnome.desktop.wm.keybindings panel-main-menu "[]"
to revert back     -> gsettings set org.gnome.desktop.wm.keybindings panel-main-menu "['<Alt>F1']"  



## Effects

Descriptions of computations to be performed at our discretion

### A bridge between
    - pure functional programming & referential transparency
    - impure FP/imperative programming & side effects

### Effect properties
    - it describes what kind of computation will be performed
    - the type signature describes the value which will be calculated
    - it separates effect description from effect execution (when externally visible side effects are produced)

### Cats Effect IO
    - any computatoin that might perform side effects
    - produces a value of type A if it's successful
    - the effect construction is separate from effect execution
    - Expressions and methods returning IOs are called effectful
    - IO compositions read like imperative program (pure FP is preserved)
    - IO is a monad
    - other transformations: (>>, *>, <*, as, void)
    - IO parallelism
        - effects are evaluated on different threads
        - synchronization and cordination are automatic (cats.syntax.parallel._)
    - IO traversal
        - useful when we want to "unwrap" double-nested containers
        - can be done in parallel
            - import cats.instances.list._ // Traverse TC instance for list
            - import cats.syntax.parallel._ // parTraverse extension method