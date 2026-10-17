import unittest
from context_result import missile_complete, craft_fired, assert_complete

class ContextResultTest(unittest.TestCase):
    def test_missile_requires_all_warheads_and_real_effects(self):
        good=dict(expectedEntities=9,spawned=9,removed=9,activeBlasts=0,completedTasks=8,changedBlocks=100)
        self.assertTrue(missile_complete(good))
        for key,value in [('spawned',1),('removed',8),('activeBlasts',1),('completedTasks',0),('completedTasks',7),('changedBlocks',0),('expectedEntities',0)]:
            self.assertFalse(missile_complete(dict(good,**{key:value})),key)

    def test_craft_consumption_is_not_a_projectile(self):
        before=dict(selected=0,alive=True,riding=True,loads=[{'ammo':10}],bulletsAlive=0,spawned={})
        after=dict(before,loads=[{'ammo':9}])
        self.assertFalse(craft_fired(before,after))
        self.assertTrue(craft_fired(before,dict(after,spawned={'shell':1})))
        self.assertTrue(craft_fired(before,dict(after,bulletsAlive=1)))
        self.assertFalse(craft_fired(before,dict(after,bulletsAlive=1,alive=False)))

    def test_incomplete_or_duplicate_is_not_success(self):
        r=dict(expectedCases=2,cases=[dict(id='a',status='PASS')],worldSaveReopen=dict(status='PASS'))
        with self.assertRaises(AssertionError):assert_complete(r)
        r['cases'].append(dict(id='a',status='PASS'))
        with self.assertRaises(AssertionError):assert_complete(r)
        r['cases'][1]['id']='b';assert_complete(r)
        r['worldSaveReopen']['status']='RUNNING'
        with self.assertRaises(AssertionError):assert_complete(r)

if __name__=='__main__':unittest.main()
